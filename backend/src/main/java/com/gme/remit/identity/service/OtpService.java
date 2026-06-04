package com.gme.remit.identity.service;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.config.OtpProperties;
import com.gme.remit.identity.domain.Otp;
import com.gme.remit.identity.repo.OtpRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OTP issue + verify.
 * <ul>
 *   <li>T-1.1.1-03 generation with configurable TTL</li>
 *   <li>T-1.1.1-04 verification, consumed on use</li>
 *   <li>T-1.1.1-11 per-identifier request rate limiter -&gt; otp-rate-limited</li>
 *   <li>T-1.1.1-12 resend-throttle, TTL from config (Platform-default tier)</li>
 *   <li>T-1.1.1-13 issue/verify metrics with channel + outcome</li>
 *   <li>T-1.1.1-14 alert on repeated OTP failures</li>
 * </ul>
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int ALERT_FAILURE_THRESHOLD = 3;

    private final OtpRepository otpRepository;
    private final OtpProperties props;
    private final MeterRegistry metrics;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    public OtpService(OtpRepository otpRepository, OtpProperties props, MeterRegistry metrics) {
        this.otpRepository = otpRepository;
        this.props = props;
        this.metrics = metrics;
    }

    /** Issue a new OTP for the identifier, enforcing rate limit (T-1.1.1-11) and resend throttle (T-1.1.1-12). */
    @Transactional
    public Otp issue(String identifier, String channel) {
        OffsetDateTime now = OffsetDateTime.now();

        // T-1.1.1-11 rate limiter: cap requests per identifier within the window.
        OffsetDateTime windowStart = now.minusSeconds(props.getRateWindowSeconds());
        long recent = otpRepository.countByIdentifierAndCreatedAtAfter(identifier, windowStart);
        if (recent >= props.getMaxRequestsPerWindow()) {
            metrics.counter("otp.issue", "channel", channel, "outcome", "rate-limited").increment();
            throw new DomainException(ErrorCode.OTP_RATE_LIMITED,
                    "Too many OTP requests; try again later");
        }

        // T-1.1.1-12 resend throttle: minimum spacing between sends.
        otpRepository.findFirstByIdentifierOrderByCreatedAtDesc(identifier).ifPresent(last -> {
            OffsetDateTime earliestNext = last.getCreatedAt().plusSeconds(props.getResendThrottleSeconds());
            if (now.isBefore(earliestNext)) {
                metrics.counter("otp.issue", "channel", channel, "outcome", "throttled").increment();
                throw new DomainException(ErrorCode.OTP_RESEND_THROTTLED,
                        "Please wait before requesting another code");
            }
        });

        String code = String.format("%06d", random.nextInt(1_000_000));
        Otp otp = otpRepository.save(Otp.issue(identifier, code, props.getTtlSeconds()));
        metrics.counter("otp.issue", "channel", channel, "outcome", "issued").increment(); // T-1.1.1-13
        return otp;
    }

    /**
     * T-1.1.1-04 Verify a code. Consumes the OTP on success. A wrong, expired, or already-consumed
     * code yields {@code otp-invalid} and never advances state.
     */
    @Transactional
    public void verify(String identifier, String code, String channel) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<Otp> usable = otpRepository
                .findFirstByIdentifierAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(identifier, now);

        if (usable.isEmpty() || !constantTimeEquals(usable.get().getCode(), code)) {
            metrics.counter("otp.verify", "channel", channel, "outcome", "invalid").increment(); // T-1.1.1-13
            recordFailure(identifier); // T-1.1.1-14
            throw new DomainException(ErrorCode.OTP_INVALID, "Invalid or expired code");
        }

        Otp otp = usable.get();
        otp.consume();
        otpRepository.save(otp);
        consecutiveFailures.remove(identifier);
        metrics.counter("otp.verify", "channel", channel, "outcome", "verified").increment();
    }

    /** T-1.1.1-14 Track consecutive failures and raise an alert past the threshold. */
    private void recordFailure(String identifier) {
        int failures = consecutiveFailures
                .computeIfAbsent(identifier, k -> new AtomicInteger())
                .incrementAndGet();
        if (failures >= ALERT_FAILURE_THRESHOLD) {
            metrics.counter("otp.verify.alert").increment();
            log.warn("ALERT repeated OTP failures identifier={} consecutiveFailures={}", identifier, failures);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
