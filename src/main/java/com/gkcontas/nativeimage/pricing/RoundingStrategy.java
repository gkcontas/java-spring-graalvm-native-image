package com.gkcontas.nativeimage.pricing;

import java.math.BigDecimal;

public interface RoundingStrategy {

    BigDecimal round(BigDecimal amount);
}
