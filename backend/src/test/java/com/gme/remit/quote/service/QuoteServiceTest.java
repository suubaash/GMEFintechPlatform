package com.gme.remit.quote.service;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.config.QuoteProperties;
import com.gme.remit.quote.domain.CorridorCatalog;
import com.gme.remit.quote.domain.Quote;
import com.gme.remit.quote.domain.QuoteStatus;
import com.gme.remit.quote.fee.FeeCalculator;
import com.gme.remit.quote.fx.SeededFxRateSource;
import com.gme.remit.quote.repo.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteServiceTest {

    private QuoteRepository repo;
    private QuoteService service;

    @BeforeEach
    void setUp() {
        repo = mock(QuoteRepository.class);
        when(repo.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new QuoteService(new CorridorCatalog(), new SeededFxRateSource(),
                new FeeCalculator(), repo, new QuoteProperties());
    }

    @Test
    void computesFeesRateAndReceiveAmount() {
        Quote q = service.createQuote("KR-NP", 1_000_000);

        // 0.5% of 1,000,000 = 5,000 (>= 3,000 min) + flat 2,000 payout = 7,000 KRW
        assertThat(q.getTotalFeesMinor()).isEqualTo(7_000);
        // quoted = mid 0.10190000 * (1 - 1%) = 0.10088100
        assertThat(q.getQuotedRate()).isEqualByComparingTo(new BigDecimal("0.10088100"));
        // receive = (1,000,000 - 7,000) KRW * 0.100881 = 100,174.833 -> NPR 100174.83 -> 10,017,483 minor
        assertThat(q.getReceiveCurrency()).isEqualTo("NPR");
        assertThat(q.getReceiveAmountMinor()).isEqualTo(10_017_483L);
        assertThat(q.getStatus()).isEqualTo(QuoteStatus.QUOTED);
    }

    @Test
    void unsupportedCorridorIsRejected() {
        assertThatThrownBy(() -> service.createQuote("KR-XX", 1_000_000))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.CORRIDOR_NOT_SUPPORTED);
    }

    @Test
    void expiredQuoteTransitionsAndCannotBeConfirmed() {
        Quote expired = new Quote.Builder()
                .corridor("KR-NP").send("KRW", 1_000_000).receive("NPR", 10_017_483)
                .midRate(new BigDecimal("0.10190000")).marginBps(100)
                .quotedRate(new BigDecimal("0.10088100")).totalFeesMinor(7_000)
                .rateSource("seed-mid-v1", OffsetDateTime.now(), 300).etaMinutes(60)
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        when(repo.findById(expired.getQuoteId())).thenReturn(Optional.of(expired));

        Quote fetched = service.getQuote(expired.getQuoteId());
        assertThat(fetched.getStatus()).isEqualTo(QuoteStatus.EXPIRED);

        assertThatThrownBy(() -> service.confirmQuote(expired.getQuoteId()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.QUOTE_EXPIRED);
    }
}
