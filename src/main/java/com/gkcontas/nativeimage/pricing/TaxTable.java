package com.gkcontas.nativeimage.pricing;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Read with Jackson from {@code pricing/tax-rates.json}. No controller method
 * mentions this type, so Spring AOT cannot infer it: it needs a binding hint,
 * and the JSON file needs a resource hint (see {@link PricingHints}).
 */
public record TaxTable(String version, Map<String, BigDecimal> rates) {
}
