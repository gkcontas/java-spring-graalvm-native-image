package com.gkcontas.nativeimage.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class HalfUpRounding implements RoundingStrategy {

    @Override
    public BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
