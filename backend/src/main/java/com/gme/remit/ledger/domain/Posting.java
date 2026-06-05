package com.gme.remit.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.util.UUID;

/** A single debit or credit line within a balanced journal voucher. Append-only. */
@Entity
@Immutable
@Table(name = "posting")
public class Posting {

    @Id
    @Column(name = "posting_id")
    private UUID postingId;

    /** Owning JV; populated by the JV's join column, read-only here for querying/ordering. */
    @Column(name = "jv_id", insertable = false, updatable = false)
    private UUID jvId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "cleared", nullable = false)
    private boolean cleared;

    protected Posting() {
    }

    static Posting of(String accountCode, Direction direction, long amountMinor, String currency,
                      UUID transferId, UUID legId, LocalDate valueDate, boolean cleared) {
        Posting p = new Posting();
        p.postingId = UUID.randomUUID();
        p.accountCode = accountCode;
        p.direction = direction;
        p.amountMinor = amountMinor;
        p.currency = currency.toUpperCase();
        p.transferId = transferId;
        p.legId = legId;
        p.valueDate = valueDate;
        p.cleared = cleared;
        return p;
    }

    public UUID getPostingId() {
        return postingId;
    }

    public UUID getJvId() {
        return jvId;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getLegId() {
        return legId;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public boolean isCleared() {
        return cleared;
    }
}
