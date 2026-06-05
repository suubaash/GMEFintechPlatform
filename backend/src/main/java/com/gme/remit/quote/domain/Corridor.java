package com.gme.remit.quote.domain;

/** Static corridor metadata: currencies, ETA, and the payout-side bank used for SWIFT. */
public record Corridor(
        String code,
        String sendCurrency,
        String receiveCurrency,
        int etaMinutes,
        String payoutBic,
        String payoutBankName,
        String payoutCountry
) {
}
