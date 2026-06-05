package com.gme.remit.swift;

import java.util.List;

/** An ordering or beneficiary customer (a person/business). */
public record SwiftCustomer(String name, List<String> addressLines, String account, String country) {

    public static SwiftCustomer of(String name, String account, String country) {
        return new SwiftCustomer(name, List.of(), account, country);
    }
}
