package com.mfin.loan.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Monetary helpers. Every amount that leaves the engine is rounded to the currency scale
 * with {@link RoundingMode#HALF_UP} (the convention used by retail lending regulators in
 * most jurisdictions); every intermediate computation runs at {@link #MC} precision so
 * that compounding does not accumulate representation error.
 *
 * <p>{@code double} is never used anywhere in this engine.</p>
 */
public final class Money {

    /** 34 significant digits - enough that intermediate error is far below one currency unit. */
    public static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    public static final RoundingMode CURRENCY_ROUNDING = RoundingMode.HALF_UP;

    public static final int DEFAULT_SCALE = 2;

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    /** Rounds to the given currency scale. Null-safe: returns zero for null. */
    public static BigDecimal round(BigDecimal value, int scale) {
        if (value == null) {
            return zero(scale);
        }
        return value.setScale(scale, CURRENCY_ROUNDING);
    }

    public static BigDecimal zero(int scale) {
        return BigDecimal.ZERO.setScale(scale, CURRENCY_ROUNDING);
    }

    public static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    /** Returns {@code value}, or zero when it is negative. */
    public static BigDecimal floorAtZero(BigDecimal value, int scale) {
        return isNegative(value) ? zero(scale) : round(value, scale);
    }

    /** Smaller of the two, treating null as zero. */
    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return left.compareTo(right) <= 0 ? left : right;
    }

    public static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
