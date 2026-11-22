package com.gkcontas.nativeimage.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Prices with tax. Two things here are invisible to static analysis: the tax
 * table is a classpath resource parsed into a record, and the rounding
 * strategy is a class chosen by name in configuration. Both work on the JVM
 * with no extra effort and both fail on a native image without the hints in
 * {@link PricingHints}.
 */
@Service
@ImportRuntimeHints(PricingHints.class)
public class PricingService {

    static final String TAX_TABLE = "pricing/tax-rates.json";

    private final TaxTable table;
    private final RoundingStrategy rounding;

    public PricingService(ObjectMapper mapper, @Value("${app.pricing.rounding}") String roundingClass) {
        this.table = load(mapper);
        this.rounding = instantiate(roundingClass);
    }

    public Quote quote(BigDecimal net, String region) {
        BigDecimal rate = table.rates().get(region.toUpperCase(Locale.ROOT));
        if (rate == null) {
            throw new UnknownRegionException(region);
        }
        BigDecimal tax = rounding.round(net.multiply(rate));
        return new Quote(net, rate, tax, net.add(tax), rounding.getClass().getSimpleName(), table.version());
    }

    public TaxTable table() {
        return table;
    }

    static TaxTable load(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(TAX_TABLE).getInputStream()) {
            return mapper.readValue(in, TaxTable.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + TAX_TABLE, e);
        }
    }

    static RoundingStrategy instantiate(String className) {
        try {
            return Class.forName(className)
                    .asSubclass(RoundingStrategy.class)
                    .getConstructor()
                    .newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("rounding strategy " + className
                    + " not found (on a native image: is it registered for reflection?)", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException(className + " is not a RoundingStrategy", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate rounding strategy " + className, e);
        }
    }
}
