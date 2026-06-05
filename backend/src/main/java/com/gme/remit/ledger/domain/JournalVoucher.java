package com.gme.remit.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A balanced, immutable journal voucher: the unit of double-entry posting. */
@Entity
@Immutable
@Table(name = "journal_voucher")
public class JournalVoucher {

    /** Input spec for one posting line. */
    public record Line(String accountCode, Direction direction, long amountMinor) {
    }

    @Id
    @Column(name = "jv_id")
    private UUID jvId;

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "leg_id")
    private UUID legId;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "posted_at", nullable = false)
    private OffsetDateTime postedAt;

    /** Built in memory by {@link #create}; persisted explicitly by the service (append-only, no cascade UPDATE). */
    @Transient
    private final List<Posting> postings = new ArrayList<>();

    protected JournalVoucher() {
    }

    /**
     * Build a balanced JV. Throws if debits != credits, the line set is empty, or an amount is non-positive.
     */
    public static JournalVoucher create(String movementType, String currency, List<Line> lines,
                                        UUID transferId, UUID legId, LocalDate valueDate, boolean cleared) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("a JV needs at least one posting");
        }
        String ccy = currency.toUpperCase();
        long debit = 0;
        long credit = 0;
        for (Line l : lines) {
            if (l.amountMinor() <= 0) {
                throw new IllegalArgumentException("posting amount must be > 0");
            }
            if (l.direction() == Direction.DEBIT) {
                debit += l.amountMinor();
            } else {
                credit += l.amountMinor();
            }
        }
        if (debit != credit) {
            throw new LedgerUnbalancedException(
                    "unbalanced JV in " + ccy + ": debit " + debit + " != credit " + credit);
        }

        JournalVoucher jv = new JournalVoucher();
        jv.jvId = UUID.randomUUID();
        jv.movementType = movementType;
        jv.currency = ccy;
        jv.amountMinor = debit;
        jv.transferId = transferId;
        jv.legId = legId;
        jv.valueDate = valueDate;
        jv.postedAt = OffsetDateTime.now();
        for (Line l : lines) {
            jv.postings.add(Posting.of(jv.jvId, l.accountCode(), l.direction(), l.amountMinor(), ccy,
                    transferId, legId, valueDate, cleared));
        }
        return jv;
    }

    public UUID getJvId() {
        return jvId;
    }

    public String getMovementType() {
        return movementType;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAmountMinor() {
        return amountMinor;
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

    public OffsetDateTime getPostedAt() {
        return postedAt;
    }

    public List<Posting> getPostings() {
        return postings;
    }
}
