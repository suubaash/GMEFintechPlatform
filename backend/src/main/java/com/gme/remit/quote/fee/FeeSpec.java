package com.gme.remit.quote.fee;

import com.gme.remit.common.config.ConfigLayer;
import com.gme.remit.quote.domain.FeeType;

/**
 * A configured fee rule. For FLAT, {@code value} is a minor-unit amount. For PERCENT, {@code value}
 * is basis points of the send amount, clamped to [{@code minMinor}, {@code maxMinor}] when &gt; 0.
 */
public record FeeSpec(
        FeeType feeType,
        FeeShape shape,
        long value,
        long minMinor,
        long maxMinor,
        ConfigLayer layer
) {
    public static FeeSpec flat(FeeType type, long minorAmount, ConfigLayer layer) {
        return new FeeSpec(type, FeeShape.FLAT, minorAmount, 0, 0, layer);
    }

    public static FeeSpec percent(FeeType type, long bps, long minMinor, long maxMinor, ConfigLayer layer) {
        return new FeeSpec(type, FeeShape.PERCENT, bps, minMinor, maxMinor, layer);
    }
}
