package com.gme.remit.swift;

/** A financial institution identified primarily by BIC (option A). */
public record SwiftInstitution(String bic, String name, String account) {

    public static SwiftInstitution ofBic(String bic, String name) {
        return new SwiftInstitution(bic, name, null);
    }
}
