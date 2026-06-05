package com.gme.remit.quote.service;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.common.money.Money;
import com.gme.remit.config.QuoteProperties;
import com.gme.remit.quote.domain.Corridor;
import com.gme.remit.quote.domain.CorridorCatalog;
import com.gme.remit.quote.domain.FeeLineItem;
import com.gme.remit.quote.domain.Quote;
import com.gme.remit.quote.fee.FeeCalculator;
import com.gme.remit.quote.fx.FxRate;
import com.gme.remit.quote.fx.FxRateSource;
import com.gme.remit.quote.repo.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Epic 2 quoting. Binds one mid-market rate, applies the FX margin, computes fee line items, and
 * derives amount_received = (send − fees) × quoted_rate reconciled to the receive currency's minor unit.
 */
@Service
public class QuoteService {

    private static final BigDecimal BPS = BigDecimal.valueOf(10_000);

    private final CorridorCatalog corridors;
    private final FxRateSource fxSource;
    private final FeeCalculator feeCalculator;
    private final QuoteRepository quotes;
    private final QuoteProperties props;

    public QuoteService(CorridorCatalog corridors, FxRateSource fxSource, FeeCalculator feeCalculator,
                        QuoteRepository quotes, QuoteProperties props) {
        this.corridors = corridors;
        this.fxSource = fxSource;
        this.feeCalculator = feeCalculator;
        this.quotes = quotes;
        this.props = props;
    }

    @Transactional
    public Quote createQuote(String corridorCode, long sendAmountMinor) {
        if (sendAmountMinor <= 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "send amount must be > 0");
        }
        Corridor corridor = corridors.require(corridorCode);

        // T-2.1.1-04 bind exactly one mid-market rate; apply margin to get the customer rate.
        FxRate mid = fxSource.fetchMidRate(corridor.sendCurrency(), corridor.receiveCurrency());
        BigDecimal marginFactor = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(props.getMarginBps()).divide(BPS, 8, RoundingMode.HALF_UP));
        BigDecimal quotedRate = mid.rate().multiply(marginFactor).setScale(8, RoundingMode.HALF_UP);

        // T-2.1.2 fees (send currency), exact integer sum.
        List<FeeLineItem> fees = feeCalculator.calculate(
                corridor.code(), corridor.sendCurrency(), sendAmountMinor);
        long totalFees = fees.stream().mapToLong(FeeLineItem::getAmountMinor).sum();

        long principalMinor = sendAmountMinor - totalFees;
        if (principalMinor <= 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "send amount does not cover fees (" + totalFees + ")");
        }

        // T-2.1.3-07 amount_received = principal(major) × quoted_rate, to receive minor units.
        BigDecimal principalMajor = Money.of(principalMinor, corridor.sendCurrency()).toMajor();
        BigDecimal receiveMajor = principalMajor.multiply(quotedRate);
        Money receive = Money.fromMajor(receiveMajor, corridor.receiveCurrency());

        OffsetDateTime now = OffsetDateTime.now();
        Quote.Builder b = new Quote.Builder()
                .corridor(corridor.code())
                .send(corridor.sendCurrency(), sendAmountMinor)
                .receive(corridor.receiveCurrency(), receive.amountMinor())
                .midRate(mid.rate())
                .marginBps(props.getMarginBps())
                .quotedRate(quotedRate)
                .totalFeesMinor(totalFees)
                .rateSource(mid.sourceId(), mid.fetchedAt(), props.getRateTtlSeconds())
                .etaMinutes(corridor.etaMinutes())
                .expiresAt(now.plusSeconds(props.getQuoteTtlSeconds()));
        fees.forEach(b::addFee);

        return quotes.save(b.build());
    }

    /** Fetch a quote, lazily transitioning QUOTED → EXPIRED past expires_at (T-2.1.3-03). */
    @Transactional
    public Quote getQuote(UUID quoteId) {
        Quote q = quotes.findById(quoteId)
                .orElseThrow(() -> new DomainException(ErrorCode.QUOTE_NOT_FOUND, "quote not found"));
        if (q.getStatus() == com.gme.remit.quote.domain.QuoteStatus.QUOTED
                && q.isExpiredAt(OffsetDateTime.now())) {
            q.markExpired();
            quotes.save(q);
        }
        return q;
    }

    /** Confirm a quote for execution; rejects expired (T-2.1.3-04) and already-confirmed quotes. */
    @Transactional
    public Quote confirmQuote(UUID quoteId) {
        Quote q = getQuote(quoteId);
        switch (q.getStatus()) {
            case EXPIRED -> throw new DomainException(ErrorCode.QUOTE_EXPIRED,
                    "quote expired; please request a new quote");
            case CONFIRMED -> throw new DomainException(ErrorCode.QUOTE_ALREADY_CONFIRMED,
                    "quote already confirmed");
            case QUOTED -> {
                q.markConfirmed();
                quotes.save(q);
            }
        }
        return q;
    }
}
