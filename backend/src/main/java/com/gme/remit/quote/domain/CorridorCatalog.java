package com.gme.remit.quote.domain;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Seeded set of supported corridors (KR hub → spokes). */
@Component
public class CorridorCatalog {

    private final Map<String, Corridor> corridors = new LinkedHashMap<>();

    public CorridorCatalog() {
        add(new Corridor("KR-NP", "KRW", "NPR", 60, "NABLNPKAXXX", "Nabil Bank (NP payout partner)", "NP"));
        add(new Corridor("KR-UZ", "KRW", "UZS", 90, "NBFAUZ2XXXX", "NBU (UZ payout partner)", "UZ"));
    }

    private void add(Corridor c) {
        corridors.put(c.code(), c);
    }

    public Corridor require(String code) {
        Corridor c = corridors.get(code.toUpperCase());
        if (c == null) {
            throw new DomainException(ErrorCode.CORRIDOR_NOT_SUPPORTED, "corridor not supported: " + code);
        }
        return c;
    }

    public List<Corridor> all() {
        return List.copyOf(corridors.values());
    }
}
