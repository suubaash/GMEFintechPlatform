package com.gme.remit.common.error;

import org.springframework.http.HttpStatus;

/** Canonical, stable error codes returned to clients (kebab-case per the API contract). */
public enum ErrorCode {

    OTP_INVALID("otp-invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    OTP_RATE_LIMITED("otp-rate-limited", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RESEND_THROTTLED("otp-resend-throttled", HttpStatus.TOO_MANY_REQUESTS),
    DUPLICATE_IDENTIFIER("duplicate-identifier", HttpStatus.CONFLICT),
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST),

    // Epic 2 — quoting
    CORRIDOR_NOT_SUPPORTED("corridor-not-supported", HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_SOURCE_UNAVAILABLE("rate-source-unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    FEE_CONFIG_MISSING("fee-config-missing", HttpStatus.UNPROCESSABLE_ENTITY),
    QUOTE_NOT_FOUND("quote-not-found", HttpStatus.NOT_FOUND),
    QUOTE_EXPIRED("quote-expired", HttpStatus.UNPROCESSABLE_ENTITY),
    QUOTE_ALREADY_CONFIRMED("quote-already-confirmed", HttpStatus.CONFLICT),

    // Transfer lifecycle
    TRANSFER_NOT_FOUND("transfer-not-found", HttpStatus.NOT_FOUND),
    ILLEGAL_TRANSITION("illegal-transition", HttpStatus.CONFLICT);

    private final String wireValue;
    private final HttpStatus status;

    ErrorCode(String wireValue, HttpStatus status) {
        this.wireValue = wireValue;
        this.status = status;
    }

    public String wireValue() {
        return wireValue;
    }

    public HttpStatus status() {
        return status;
    }
}
