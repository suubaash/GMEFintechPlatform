package com.gme.remit.identity.api.dto;

import java.time.OffsetDateTime;

/**
 * OTP issue acknowledgement.
 * <p>{@code devCode} is populated only while there is no real SMS/email delivery adapter
 * (notifications land in a later workstream). It lets the web demo complete the flow and
 * MUST be removed once a delivery provider is wired in.
 */
public record OtpResponse(
        String status,
        OffsetDateTime expiresAt,
        String devCode
) {
}
