package com.gme.remit.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** T-1.1.1-01 Party/account aggregate. A new registration starts UNVERIFIED. */
@Entity
@Table(name = "party")
public class Party {

    @Id
    @Column(name = "party_id")
    private UUID partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false)
    private PartyType partyType = PartyType.INDIVIDUAL;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status = AccountStatus.UNVERIFIED;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Party() {
    }

    public static Party newIndividual(String email, String phone, String fullName) {
        Party p = new Party();
        p.partyId = UUID.randomUUID();
        p.partyType = PartyType.INDIVIDUAL;
        p.email = email;
        p.phone = phone;
        p.fullName = fullName;
        p.status = AccountStatus.UNVERIFIED;
        OffsetDateTime now = OffsetDateTime.now();
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public PartyType getPartyType() {
        return partyType;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getFullName() {
        return fullName;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
