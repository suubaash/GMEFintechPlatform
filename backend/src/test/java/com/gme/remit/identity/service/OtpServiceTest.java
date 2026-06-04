package com.gme.remit.identity.service;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.config.OtpProperties;
import com.gme.remit.identity.domain.Otp;
import com.gme.remit.identity.repo.OtpRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpServiceTest {

    private OtpRepository otpRepository;
    private OtpService service;

    @BeforeEach
    void setUp() {
        otpRepository = mock(OtpRepository.class);
        OtpProperties props = new OtpProperties(); // platform defaults
        service = new OtpService(otpRepository, props, new SimpleMeterRegistry());
    }

    @Test // T-1.1.1-21 happy path: issue creates a 6-digit code and persists it
    void issuesSixDigitCode() {
        when(otpRepository.countByIdentifierAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(otpRepository.findFirstByIdentifierOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(otpRepository.save(any(Otp.class))).thenAnswer(inv -> inv.getArgument(0));

        Otp otp = service.issue("you@example.com", "email");

        assertThat(otp.getCode()).matches("\\d{6}");
        assertThat(otp.isConsumed()).isFalse();
        verify(otpRepository).save(any(Otp.class));
    }

    @Test // T-1.1.1-21 happy path: a matching code verifies and is consumed
    void verifiesAndConsumes() {
        Otp otp = Otp.issue("you@example.com", "123456", 300);
        when(otpRepository.findFirstByIdentifierAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                eq("you@example.com"), any(OffsetDateTime.class))).thenReturn(Optional.of(otp));

        service.verify("you@example.com", "123456", "email");

        assertThat(otp.isConsumed()).isTrue();
        verify(otpRepository).save(otp);
    }

    @Test // T-1.1.1-22 no usable OTP (expired/consumed) -> otp-invalid
    void expiredOrConsumedYieldsOtpInvalid() {
        when(otpRepository.findFirstByIdentifierAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any(OffsetDateTime.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("you@example.com", "123456", "email"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.OTP_INVALID);
    }

    @Test // T-1.1.1-22 wrong code -> otp-invalid and OTP not consumed
    void wrongCodeYieldsOtpInvalid() {
        Otp otp = Otp.issue("you@example.com", "123456", 300);
        when(otpRepository.findFirstByIdentifierAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any(OffsetDateTime.class))).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.verify("you@example.com", "000000", "email"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.OTP_INVALID);
        assertThat(otp.isConsumed()).isFalse();
        verify(otpRepository, never()).save(any());
    }

    @Test // T-1.1.1-24 too many requests in the window -> otp-rate-limited
    void rateLimitedAfterMax() {
        when(otpRepository.countByIdentifierAndCreatedAtAfter(anyString(), any())).thenReturn(5L); // == max default

        assertThatThrownBy(() -> service.issue("you@example.com", "email"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.OTP_RATE_LIMITED);
        verify(otpRepository, never()).save(any());
    }
}
