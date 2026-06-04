package com.gme.remit.identity.api.dto;

import com.gme.remit.identity.domain.Party;

import java.time.OffsetDateTime;
import java.util.UUID;

/** T-1.1.1-09 Registration response DTO. PII is masked; the raw identifier is never echoed back. */
public record PartyResponse(
        UUID partyId,
        String partyType,
        String status,
        String maskedIdentifier,
        OffsetDateTime createdAt
) {
    public static PartyResponse from(Party p) {
        String id = p.getEmail() != null ? p.getEmail() : p.getPhone();
        return new PartyResponse(
                p.getPartyId(),
                p.getPartyType().name(),
                p.getStatus().name(),
                mask(id),
                p.getCreatedAt()
        );
    }

    private static String mask(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        int at = identifier.indexOf('@');
        if (at > 0) { // email: keep first char + domain
            String local = identifier.substring(0, at);
            String visible = local.substring(0, 1);
            return visible + "***" + identifier.substring(at);
        }
        // phone or other: keep last 4
        int keep = Math.min(4, identifier.length());
        return "***" + identifier.substring(identifier.length() - keep);
    }
}
