package com.gkcontas.nativeimage.product;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final ObjectProvider<AuditTrail> audit;

    public ProductService(ProductRepository products, ObjectProvider<AuditTrail> audit) {
        this.products = products;
        this.audit = audit;
    }

    @Transactional
    public Product create(String sku, String name, String category, BigDecimal price) {
        if (products.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }
        try {
            Product saved = products.saveAndFlush(new Product(sku, name, category, price));
            audit.ifAvailable(AuditTrail::productCreated);
            return saved;
        } catch (DataIntegrityViolationException race) {
            // Two requests with the same sku can both pass the exists check; the
            // unique constraint is the real guarantee.
            throw new DuplicateSkuException(sku);
        }
    }

    @Transactional(readOnly = true)
    public Product get(long id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Product> byCategory(String category, int limit) {
        return products.findByCategoryOrderByIdAsc(category, Limit.of(limit));
    }
}
