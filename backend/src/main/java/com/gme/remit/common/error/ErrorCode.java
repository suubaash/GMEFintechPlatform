package com.gme.remit.common.error;

import org.springframework.http.HttpStatus;

/** Canonical, stable error codes returned to clients (kebab-case per the API contract). */
public enum ErrorCode {

    OTP_INVALID("otp-invalid", HttpStatus.UNPROCESSABLE_ENTITY),
    OTP_RATE_LIMITED("otp-rate-limited", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RESEND_THROTTLED("otp-resend-throttled", HttpStatus.TOO_MANY_REQUESTS),
    DUPLICATE_IDENTIFIER("duplicate-identifier", HttpStatus.CONFLICT),
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST);

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
