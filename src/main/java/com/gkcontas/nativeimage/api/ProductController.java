package com.gkcontas.nativeimage.api;

import com.gkcontas.nativeimage.pricing.PricingService;
import com.gkcontas.nativeimage.product.Product;
import com.gkcontas.nativeimage.product.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService products;
    private final PricingService pricing;

    public ProductController(ProductService products, PricingService pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product p = products.create(request.sku(), request.name(), request.category(), request.price());
        return ResponseEntity.created(URI.create("/api/products/" + p.getId())).body(ProductResponse.of(p));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return ProductResponse.of(products.get(id));
    }

    @GetMapping
    public List<ProductResponse> byCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must be at most 100") int limit) {
        return products.byCategory(category, limit).stream().map(ProductResponse::of).toList();
    }

    @GetMapping("/{id}/quote")
    public QuoteResponse quote(@PathVariable long id, @RequestParam String region) {
        Product p = products.get(id);
        return QuoteResponse.of(p.getId(), p.getSku(), region, pricing.quote(p.getPrice(), region));
    }
}
