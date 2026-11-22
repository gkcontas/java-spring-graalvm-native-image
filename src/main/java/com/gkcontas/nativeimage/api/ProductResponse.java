package com.gkcontas.nativeimage.api;

import com.gkcontas.nativeimage.product.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(long id, String sku, String name, String category, BigDecimal price, Instant createdAt) {

    static ProductResponse of(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getPrice(), p.getCreatedAt());
    }
}
