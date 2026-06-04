package com.gme.remit.identity.domain;

/** Account lifecycle. New registrations land in UNVERIFIED (T-1.1.1-01). */
public enum AccountStatus {
    UNVERIFIED,
    PENDING_KYC,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
