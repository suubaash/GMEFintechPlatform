package com.gme.remit.quote.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Request a quote. {@code sendAmountMinor} is in the send currency's minor units (whole KRW). */
public record QuoteRequest(
        @NotBlank(message = "corridor is required")
        String corridor,

        @Positive(message = "sendAmountMinor must be > 0")
        long sendAmountMinor
) {
}
