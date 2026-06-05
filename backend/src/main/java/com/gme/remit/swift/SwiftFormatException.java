package com.gme.remit.swift;

/** Raised when a value cannot be represented in a SWIFT field. */
public class SwiftFormatException extends RuntimeException {
    public SwiftFormatException(String message) {
        super(message);
    }
}
