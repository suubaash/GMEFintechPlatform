package com.gme.remit.quote.fx;

/** T-2.1.1-03 FX source adapter: fetch a mid-market rate for a supported pair. */
public interface FxRateSource {

    /**
     * @throws com.gme.remit.common.error.DomainException rate-source-unavailable if the pair is not
     *         served or a fetched rate is malformed (zero/negative).
     */
    FxRate fetchMidRate(String base, String quote);

    String sourceId();
}
