package com.gkcontas.nativeimage.api;

import com.gkcontas.nativeimage.pricing.Quote;
import java.math.BigDecimal;

public record QuoteResponse(long productId, String sku, String region, BigDecimal net, BigDecimal taxRate,
                            BigDecimal tax, BigDecimal gross, String rounding, String tableVersion) {

    static QuoteResponse of(long productId, String sku, String region, Quote q) {
        return new QuoteResponse(productId, sku, region, q.net(), q.taxRate(), q.tax(), q.gross(), q.rounding(),
                q.tableVersion());
    }
}
