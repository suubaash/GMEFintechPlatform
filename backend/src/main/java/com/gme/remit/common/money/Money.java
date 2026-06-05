package com.gme.remit.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable money value type: signed minor units + ISO-4217 currency, currency-safe arithmetic.
 * Negative amounts are allowed (ledger deltas). Mirrors {@code remit_core.money.Money}.
 */
public record Money(long amountMinor, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        currency = currency.toUpperCase();
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    /** Build from a major-unit amount, rounding HALF_UP to the currency's minor units. */
    public static Money fromMajor(BigDecimal major, String currency) {
        int dp = Currencies.minorUnits(currency);
        BigDecimal scaled = major.setScale(dp, RoundingMode.HALF_UP).movePointRight(dp);
        return new Money(scaled.longValueExact(), currency);
    }

    public BigDecimal toMajor() {
        int dp = Currencies.minorUnits(currency);
        return BigDecimal.valueOf(amountMinor).movePointLeft(dp);
    }

    private void sameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    public Money plus(Money other) {
        sameCurrency(other);
        return new Money(amountMinor + other.amountMinor, currency);
    }

    public Money minus(Money other) {
        sameCurrency(other);
        return new Money(amountMinor - other.amountMinor, currency);
    }

    public Money negate() {
        return new Money(-amountMinor, currency);
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    @Override
    public String toString() {
        return toMajor().toPlainString() + " " + currency;
    }
}
