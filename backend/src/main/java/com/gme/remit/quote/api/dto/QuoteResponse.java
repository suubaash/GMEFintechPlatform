package com.gme.remit.quote.api.dto;

import com.gme.remit.common.money.Money;
import com.gme.remit.quote.domain.Quote;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** T-2.1.3-09 Quote API response payload. */
public record QuoteResponse(
        UUID quoteId,
        String corridor,
        String sendCurrency,
        long sendAmountMinor,
        String sendAmount,
        String receiveCurrency,
        long receiveAmountMinor,
        String receiveAmount,
        BigDecimal midRate,
        int marginBps,
        BigDecimal quotedRate,
        long totalFeesMinor,
        String totalFees,
        List<FeeLineDto> fees,
        String rateSourceId,
        long rateTtlSeconds,
        int etaMinutes,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
    public record FeeLineDto(String type, long amountMinor, String currency, String resolvedLayer) {
    }

    public static QuoteResponse from(Quote q) {
        List<FeeLineDto> fees = q.getFees().stream()
                .map(f -> new FeeLineDto(f.getFeeType().name(), f.getAmountMinor(),
                        f.getCurrency(), f.getResolvedLayer().name()))
                .toList();
        return new QuoteResponse(
                q.getQuoteId(),
                q.getCorridor(),
                q.getSendCurrency(),
                q.getSendAmountMinor(),
                Money.of(q.getSendAmountMinor(), q.getSendCurrency()).toMajor().toPlainString(),
                q.getReceiveCurrency(),
                q.getReceiveAmountMinor(),
                Money.of(q.getReceiveAmountMinor(), q.getReceiveCurrency()).toMajor().toPlainString(),
                q.getMidRate(),
                q.getMarginBps(),
                q.getQuotedRate(),
                q.getTotalFeesMinor(),
                Money.of(q.getTotalFeesMinor(), q.getSendCurrency()).toMajor().toPlainString(),
                fees,
                q.getRateSourceId(),
                q.getRateTtlSeconds(),
                q.getEtaMinutes(),
                q.getStatus().name(),
                q.getCreatedAt(),
                q.getExpiresAt()
        );
    }
}
