package com.gme.remit.quote.fx;

import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Reference FX source with seeded mid-market rates (stands in for a live provider feed).
 * T-2.1.1-04 binds exactly one rate per pair; T-2.1.1-11 rejects zero/negative; unknown pairs
 * surface rate-source-unavailable (T-2.1.1-10).
 */
@Component
public class SeededFxRateSource implements FxRateSource {

    private static final String SOURCE_ID = "seed-mid-v1";

    // 1 unit of base = N units of quote (illustrative mid-market levels).
    private static final Map<String, BigDecimal> RATES = Map.of(
            "KRW->NPR", new BigDecimal("0.10190000"),
            "KRW->UZS", new BigDecimal("9.45000000"),
            "USD->NPR", new BigDecimal("133.20000000"),
            "USD->KRW", new BigDecimal("1361.50000000")
    );

    @Override
    public FxRate fetchMidRate(String base, String quote) {
        String pair = base.toUpperCase() + "->" + quote.toUpperCase();
        BigDecimal rate = RATES.get(pair);
        if (rate == null) {
            throw new DomainException(ErrorCode.RATE_SOURCE_UNAVAILABLE,
                    "no rate available for " + pair);
        }
        if (rate.signum() <= 0) { // T-2.1.1-11 never bind a malformed rate
            throw new DomainException(ErrorCode.RATE_SOURCE_UNAVAILABLE,
                    "malformed rate for " + pair);
        }
        return new FxRate(base.toUpperCase(), quote.toUpperCase(), rate, SOURCE_ID, OffsetDateTime.now());
    }

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }
}
