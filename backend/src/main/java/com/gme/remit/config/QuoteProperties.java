package com.gme.remit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Platform-default quoting config (Appendix A tier). */
@ConfigurationProperties(prefix = "remit.quote")
public class QuoteProperties {

    /** FX margin applied to the mid-market rate, in basis points (100 = 1%). */
    private int marginBps = 100;

    /** How long a quote stays confirmable. */
    private long quoteTtlSeconds = 900;

    /** How long a fetched rate is reusable before a fresh fetch. */
    private long rateTtlSeconds = 300;

    public int getMarginBps() {
        return marginBps;
    }

    public void setMarginBps(int marginBps) {
        this.marginBps = marginBps;
    }

    public long getQuoteTtlSeconds() {
        return quoteTtlSeconds;
    }

    public void setQuoteTtlSeconds(long quoteTtlSeconds) {
        this.quoteTtlSeconds = quoteTtlSeconds;
    }

    public long getRateTtlSeconds() {
        return rateTtlSeconds;
    }

    public void setRateTtlSeconds(long rateTtlSeconds) {
        this.rateTtlSeconds = rateTtlSeconds;
    }
}
