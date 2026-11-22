package com.gkcontas.nativeimage.pricing;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * What the closed world cannot see on its own: the JSON resource, the record
 * Jackson binds it to, and every rounding strategy that configuration may name.
 * A strategy added later and not listed here works in every JVM test and fails
 * only in the native binary.
 */
public class PricingHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern(PricingService.TAX_TABLE);
        new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), TaxTable.class);
        hints.reflection()
                .registerType(HalfEvenRounding.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
                .registerType(HalfUpRounding.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
    }
}
