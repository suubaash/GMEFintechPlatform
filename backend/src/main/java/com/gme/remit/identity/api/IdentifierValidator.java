package com.gme.remit.identity.api;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;

import java.util.regex.Pattern;

/** T-1.1.1-06 Validate the registration identifier matches the declared channel. */
public final class IdentifierValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");

    private IdentifierValidator() {
    }

    public static void validate(String channel, String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        boolean ok = switch (channel) {
            case "email" -> EMAIL.matcher(value).matches();
            case "phone" -> PHONE.matcher(value).matches();
            default -> false;
        };
        if (!ok) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "identifier is not a valid " + channel);
        }
    }
}
