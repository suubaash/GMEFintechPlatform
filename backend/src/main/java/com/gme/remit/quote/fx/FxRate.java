package com.gme.remit.quote.fx;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A fetched mid-market rate: 1 unit of {@code base} = {@code rate} units of {@code quote}. */
public record FxRate(String base, String quote, BigDecimal rate, String sourceId, OffsetDateTime fetchedAt) {
}
