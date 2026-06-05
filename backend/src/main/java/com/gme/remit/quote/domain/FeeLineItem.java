package com.gme.remit.quote.domain;

import com.gme.remit.common.config.ConfigLayer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** One fee line on a quote, recording which config layer it resolved from (T-2.1.2-04). */
@Entity
@Table(name = "fee_line_item")
public class FeeLineItem {

    @Id
    @Column(name = "fee_id")
    private UUID feeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_layer", nullable = false)
    private ConfigLayer resolvedLayer;

    protected FeeLineItem() {
    }

    public static FeeLineItem of(FeeType type, long amountMinor, String currency, ConfigLayer layer) {
        FeeLineItem f = new FeeLineItem();
        f.feeId = UUID.randomUUID();
        f.feeType = type;
        f.amountMinor = amountMinor;
        f.currency = currency.toUpperCase();
        f.resolvedLayer = layer;
        return f;
    }

    public UUID getFeeId() {
        return feeId;
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public ConfigLayer getResolvedLayer() {
        return resolvedLayer;
    }
}
