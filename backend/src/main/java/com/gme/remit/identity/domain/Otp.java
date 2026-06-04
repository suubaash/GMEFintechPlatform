package com.gme.remit.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** T-1.1.1-02 One-time passcode. Single-use ({@code consumed}) with a TTL ({@code expiresAt}). */
@Entity
@Table(name = "otp")
public class Otp {

    @Id
    @Column(name = "otp_id")
    private UUID otpId;

    @Column(name = "identifier", nullable = false)
    private String identifier;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected Otp() {
    }

    public static Otp issue(String identifier, String code, long ttlSeconds) {
        Otp o = new Otp();
        o.otpId = UUID.randomUUID();
        o.identifier = identifier;
        o.code = code;
        o.consumed = false;
        OffsetDateTime now = OffsetDateTime.now();
        o.createdAt = now;
        o.expiresAt = now.plusSeconds(ttlSeconds);
        return o;
    }

    public boolean isExpired(OffsetDateTime at) {
        return at.isAfter(expiresAt);
    }

    /** Mark the OTP consumed so it cannot be reused (T-1.1.1-04). */
    public void consume() {
        this.consumed = true;
    }

    public UUID getOtpId() {
        return otpId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getCode() {
        return code;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
