package com.gme.remit.common.money;

import java.util.Map;

/** ISO-4217 minor-unit table (default 2). Mirrors the platform's canonical money model. */
public final class Currencies {

    private static final Map<String, Integer> MINOR_UNITS = Map.ofEntries(
            Map.entry("JPY", 0), Map.entry("KRW", 0), Map.entry("VND", 0),
            Map.entry("CLP", 0), Map.entry("ISK", 0),
            Map.entry("BHD", 3), Map.entry("KWD", 3), Map.entry("OMR", 3), Map.entry("TND", 3),
            Map.entry("NPR", 2), Map.entry("USD", 2), Map.entry("EUR", 2), Map.entry("GBP", 2),
            Map.entry("INR", 2), Map.entry("UZS", 2)
    );

    private Currencies() {
    }

    public static int minorUnits(String currency) {
        return MINOR_UNITS.getOrDefault(currency.toUpperCase(), 2);
    }
}
