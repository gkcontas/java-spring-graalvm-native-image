package com.gkcontas.nativeimage.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bulk data for the load test, generated inside PostgreSQL. */
@RestController
@Validated
public class AdminController {

    private static final String[] CATEGORIES = {"books", "games", "tools", "garden", "kitchen"};

    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/api/admin/seed")
    @Transactional
    public Map<String, Object> seed(@RequestParam @Min(value = 1, message = "count must be at least 1")
                                    @Max(value = 1_000_000, message = "count must be at most 1000000") int count) {
        jdbc.execute("TRUNCATE product RESTART IDENTITY");
        int inserted = jdbc.update("""
                INSERT INTO product (sku, name, category, price)
                SELECT 'SKU-' || g, 'Product ' || g, (?::text[])[1 + g % 5], round((1 + (g * 7919 % 100000) / 100.0)::numeric, 2)
                FROM generate_series(1, ?) g
                """, "{" + String.join(",", CATEGORIES) + "}", count);
        return Map.of("inserted", inserted);
    }
}
