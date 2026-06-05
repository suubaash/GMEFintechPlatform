package com.gme.remit.quote.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A price quote for a corridor transfer. QUOTED on creation; EXPIRED past expires_at. */
@Entity
@Table(name = "quote")
public class Quote {

    @Id
    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "corridor", nullable = false)
    private String corridor;

    @Column(name = "send_currency", nullable = false)
    private String sendCurrency;

    @Column(name = "send_amount_minor", nullable = false)
    private long sendAmountMinor;

    @Column(name = "receive_currency", nullable = false)
    private String receiveCurrency;

    @Column(name = "receive_amount_minor", nullable = false)
    private long receiveAmountMinor;

    @Column(name = "mid_rate", nullable = false)
    private BigDecimal midRate;

    @Column(name = "margin_bps", nullable = false)
    private int marginBps;

    @Column(name = "quoted_rate", nullable = false)
    private BigDecimal quotedRate;

    @Column(name = "total_fees_minor", nullable = false)
    private long totalFeesMinor;

    @Column(name = "rate_source_id", nullable = false)
    private String rateSourceId;

    @Column(name = "fetch_ts", nullable = false)
    private OffsetDateTime fetchTs;

    @Column(name = "rate_ttl_seconds", nullable = false)
    private long rateTtlSeconds;

    @Column(name = "eta_minutes", nullable = false)
    private int etaMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QuoteStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "quote_id", nullable = false)
    private List<FeeLineItem> fees = new ArrayList<>();

    protected Quote() {
    }

    /** Builder used by QuoteService once all amounts are computed. */
    public static class Builder {
        private final Quote q = new Quote();

        public Builder() {
            q.quoteId = UUID.randomUUID();
            q.status = QuoteStatus.QUOTED;
            q.createdAt = OffsetDateTime.now();
        }

        public Builder corridor(String v) { q.corridor = v; return this; }
        public Builder send(String ccy, long minor) { q.sendCurrency = ccy.toUpperCase(); q.sendAmountMinor = minor; return this; }
        public Builder receive(String ccy, long minor) { q.receiveCurrency = ccy.toUpperCase(); q.receiveAmountMinor = minor; return this; }
        public Builder midRate(BigDecimal v) { q.midRate = v; return this; }
        public Builder marginBps(int v) { q.marginBps = v; return this; }
        public Builder quotedRate(BigDecimal v) { q.quotedRate = v; return this; }
        public Builder totalFeesMinor(long v) { q.totalFeesMinor = v; return this; }
        public Builder rateSource(String id, OffsetDateTime fetchTs, long ttlSeconds) {
            q.rateSourceId = id; q.fetchTs = fetchTs; q.rateTtlSeconds = ttlSeconds; return this;
        }
        public Builder etaMinutes(int v) { q.etaMinutes = v; return this; }
        public Builder expiresAt(OffsetDateTime v) { q.expiresAt = v; return this; }
        public Builder addFee(FeeLineItem f) { q.fees.add(f); return this; }

        public Quote build() {
            return q;
        }
    }

    public boolean isExpiredAt(OffsetDateTime at) {
        return at.isAfter(expiresAt);
    }

    public void markExpired() {
        this.status = QuoteStatus.EXPIRED;
    }

    public void markConfirmed() {
        this.status = QuoteStatus.CONFIRMED;
    }

    public UUID getQuoteId() { return quoteId; }
    public String getCorridor() { return corridor; }
    public String getSendCurrency() { return sendCurrency; }
    public long getSendAmountMinor() { return sendAmountMinor; }
    public String getReceiveCurrency() { return receiveCurrency; }
    public long getReceiveAmountMinor() { return receiveAmountMinor; }
    public BigDecimal getMidRate() { return midRate; }
    public int getMarginBps() { return marginBps; }
    public BigDecimal getQuotedRate() { return quotedRate; }
    public long getTotalFeesMinor() { return totalFeesMinor; }
    public String getRateSourceId() { return rateSourceId; }
    public OffsetDateTime getFetchTs() { return fetchTs; }
    public long getRateTtlSeconds() { return rateTtlSeconds; }
    public int getEtaMinutes() { return etaMinutes; }
    public QuoteStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public List<FeeLineItem> getFees() { return fees; }
}
