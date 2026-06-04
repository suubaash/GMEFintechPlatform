package com.gme.remit.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request to issue a registration OTP. {@code channel} selects how {@code identifier} is interpreted. */
public record OtpRequest(
        @NotBlank(message = "identifier is required")
        String identifier,

        @NotBlank(message = "channel is required")
        @Pattern(regexp = "email|phone", message = "channel must be 'email' or 'phone'")
        String channel
) {
}
