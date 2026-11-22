package com.gkcontas.nativeimage.pricing;

import java.math.BigDecimal;

public record Quote(BigDecimal net, BigDecimal taxRate, BigDecimal tax, BigDecimal gross,
                    String rounding, String tableVersion) {
}
