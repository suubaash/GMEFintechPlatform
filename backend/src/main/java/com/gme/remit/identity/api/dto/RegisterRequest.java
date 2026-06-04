package com.gme.remit.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Submit registration: the issued OTP {@code code} plus the applicant's name. */
public record RegisterRequest(
        @NotBlank(message = "identifier is required")
        String identifier,

        @NotBlank(message = "channel is required")
        @Pattern(regexp = "email|phone", message = "channel must be 'email' or 'phone'")
        String channel,

        @NotBlank(message = "code is required")
        String code,

        @NotBlank(message = "fullName is required")
        String fullName
) {
}
