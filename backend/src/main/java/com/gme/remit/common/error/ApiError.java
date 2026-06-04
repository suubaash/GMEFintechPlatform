package com.gme.remit.common.error;

import java.time.OffsetDateTime;

/** Uniform error body returned for all handled failures. */
public record ApiError(String code, String message, OffsetDateTime timestamp) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, OffsetDateTime.now());
    }
}
