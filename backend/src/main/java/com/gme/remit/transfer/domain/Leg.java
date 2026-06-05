package com.gme.remit.transfer.domain;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An internal execution segment of a transfer (PAYIN / CONVERSION / PAYOUT). */
@Entity
@Table(name = "leg")
public class Leg {

    @Id
    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private LegKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LegStatus status;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Leg() {
    }

    public static Leg create(UUID transferId, LegKind kind, String currency, long amountMinor, int sequence) {
        Leg leg = new Leg();
        leg.legId = UUID.randomUUID();
        leg.transferId = transferId;
        leg.kind = kind;
        leg.status = LegStatus.CREATED;
        leg.currency = currency.toUpperCase();
        leg.amountMinor = amountMinor;
        leg.sequence = sequence;
        OffsetDateTime now = OffsetDateTime.now();
        leg.createdAt = now;
        leg.updatedAt = now;
        return leg;
    }

    public void transitionTo(LegStatus to) {
        if (!status.canTransitionTo(to)) {
            throw new DomainException(ErrorCode.ILLEGAL_TRANSITION,
                    "leg " + status + " -> " + to + " not allowed");
        }
        this.status = to;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getLegId() { return legId; }
    public UUID getTransferId() { return transferId; }
    public LegKind getKind() { return kind; }
    public LegStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public long getAmountMinor() { return amountMinor; }
    public int getSequence() { return sequence; }
}
