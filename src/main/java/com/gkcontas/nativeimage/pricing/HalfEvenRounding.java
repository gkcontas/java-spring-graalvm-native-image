package com.gkcontas.nativeimage.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Banker's rounding: ties go to the even cent, so rounding errors do not drift upwards. */
public class HalfEvenRounding implements RoundingStrategy {

    @Override
    public BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN);
    }
}
