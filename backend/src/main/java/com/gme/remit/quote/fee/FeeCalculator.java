package com.gme.remit.quote.fee;

import com.gme.remit.common.config.ConfigLayer;
import com.gme.remit.common.error.DomainException;
import com.gme.remit.common.error.ErrorCode;
import com.gme.remit.quote.domain.FeeLineItem;
import com.gme.remit.quote.domain.FeeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T-2.1.2 Fee computation. Seeded platform-default config per corridor; computes one line item per
 * configured fee with the resolved layer recorded. Refuses to quote with fee-config-missing when no
 * config resolves (never silently zero). Total is the exact integer sum of line items.
 */
@Component
public class FeeCalculator {

    // Platform-default seed. Route/Corridor/Tenant overrides would layer above this later.
    private static final Map<String, List<FeeSpec>> CONFIG = new LinkedHashMap<>();

    static {
        CONFIG.put("KR-NP", List.of(
                FeeSpec.percent(FeeType.SERVICE, 50, 3000, 0, ConfigLayer.PLATFORM), // 0.5%, min 3000 KRW
                FeeSpec.flat(FeeType.PAYOUT, 2000, ConfigLayer.PLATFORM)
        ));
        CONFIG.put("KR-UZ", List.of(
                FeeSpec.percent(FeeType.SERVICE, 50, 3000, 0, ConfigLayer.PLATFORM),
                FeeSpec.flat(FeeType.PAYOUT, 3000, ConfigLayer.PLATFORM)
        ));
    }

    /** Compute fee line items in the send currency. */
    public List<FeeLineItem> calculate(String corridor, String sendCurrency, long sendAmountMinor) {
        List<FeeSpec> specs = CONFIG.get(corridor.toUpperCase());
        if (specs == null || specs.isEmpty()) {
            throw new DomainException(ErrorCode.FEE_CONFIG_MISSING,
                    "no fee config resolves for corridor " + corridor);
        }
        return specs.stream()
                .map(s -> FeeLineItem.of(s.feeType(), amountFor(s, sendAmountMinor), sendCurrency, s.layer()))
                .toList();
    }

    private long amountFor(FeeSpec s, long sendAmountMinor) {
        if (s.shape() == FeeShape.FLAT) {
            return s.value();
        }
        long raw = BigDecimal.valueOf(sendAmountMinor)
                .multiply(BigDecimal.valueOf(s.value()))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
        if (s.minMinor() > 0 && raw < s.minMinor()) {
            raw = s.minMinor();
        }
        if (s.maxMinor() > 0 && raw > s.maxMinor()) {
            raw = s.maxMinor();
        }
        return raw;
    }
}
