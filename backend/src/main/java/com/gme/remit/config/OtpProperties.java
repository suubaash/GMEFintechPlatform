package com.gme.remit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * T-1.1.1-12 OTP config. These are the <b>Platform default</b> tier of the glossary's
 * config precedence (Route &gt; Corridor &gt; Tenant &gt; Platform default). Higher tiers
 * can be layered later without touching call sites.
 */
@ConfigurationProperties(prefix = "remit.otp")
public class OtpProperties {

    /** OTP time-to-live in seconds. */
    private long ttlSeconds = 300;

    /** Minimum spacing between resend requests for one identifier. */
    private long resendThrottleSeconds = 30;

    /** Max OTP requests per identifier within the rate window. */
    private int maxRequestsPerWindow = 5;

    /** Rate-limit window length in seconds. */
    private long rateWindowSeconds = 3600;

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getResendThrottleSeconds() {
        return resendThrottleSeconds;
    }

    public void setResendThrottleSeconds(long resendThrottleSeconds) {
        this.resendThrottleSeconds = resendThrottleSeconds;
    }

    public int getMaxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    public void setMaxRequestsPerWindow(int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    public long getRateWindowSeconds() {
        return rateWindowSeconds;
    }

    public void setRateWindowSeconds(long rateWindowSeconds) {
        this.rateWindowSeconds = rateWindowSeconds;
    }
}
