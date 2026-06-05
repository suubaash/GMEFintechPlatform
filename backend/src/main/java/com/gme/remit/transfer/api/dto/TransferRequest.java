package com.gme.remit.transfer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Execute a transfer against a confirmed quote. */
public record TransferRequest(
        @NotNull(message = "quoteId is required")
        UUID quoteId,

        @NotBlank(message = "senderName is required")
        String senderName,

        @NotBlank(message = "recipientName is required")
        String recipientName,

        String recipientAccount,
        String recipientBankBic
) {
}
