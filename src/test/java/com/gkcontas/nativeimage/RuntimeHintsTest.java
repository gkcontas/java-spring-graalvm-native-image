package com.gkcontas.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.nativeimage.diagnostics.ClosedWorldProbes;
import com.gkcontas.nativeimage.diagnostics.DiagnosticsHints;
import com.gkcontas.nativeimage.diagnostics.Farewell;
import com.gkcontas.nativeimage.diagnostics.Greeter;
import com.gkcontas.nativeimage.diagnostics.HintedPayload;
import com.gkcontas.nativeimage.diagnostics.RegisteredPlugin;
import com.gkcontas.nativeimage.diagnostics.UnhintedPayload;
import com.gkcontas.nativeimage.diagnostics.UnregisteredPlugin;
import com.gkcontas.nativeimage.pricing.PricingHints;
import com.gkcontas.nativeimage.pricing.RoundingStrategy;
import com.gkcontas.nativeimage.pricing.TaxTable;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.annotation.ReflectiveRuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Native-image metadata checked on the JVM, in milliseconds, instead of after
 * a multi-minute native compilation.
 */
class RuntimeHintsTest {

    private final RuntimeHints hints = new RuntimeHints();

    @BeforeEach
    void register() {
        ClassLoader loader = getClass().getClassLoader();
        new PricingHints().registerHints(hints, loader);
        new DiagnosticsHints().registerHints(hints, loader);
        new ReflectiveRuntimeHintsRegistrar().registerRuntimeHints(hints, ClosedWorldProbes.class);
    }

    @Test
    void taxTableResourceAndItsBindingTypeAreRegistered() {
        assertThat(RuntimeHintsPredicates.resource().forResource("pricing/tax-rates.json")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(TaxTable.class)).accepts(hints);
    }

    @Test
    void everyRoundingStrategyOnTheClasspathCanBeInstantiatedByName() throws ClassNotFoundException {
        // The trap this guards: someone adds a strategy, sets it in configuration,
        // every JVM test passes and the native binary dies at startup.
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(RoundingStrategy.class));
        List<String> strategies = scanner.findCandidateComponents("com.gkcontas.nativeimage").stream()
                .map(BeanDefinition::getBeanClassName).toList();

        assertThat(strategies).hasSizeGreaterThanOrEqualTo(2);
        for (String name : strategies) {
            assertThat(RuntimeHintsPredicates.reflection().onType(Class.forName(name))
                    .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
                    .as(name).accepts(hints);
        }
    }

    @Test
    void hintedSideOfEachProbeIsRegistered() {
        assertThat(RuntimeHintsPredicates.reflection().onType(RegisteredPlugin.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("diagnostics/registered.txt")).accepts(hints);
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(Greeter.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(HintedPayload.class)).accepts(hints);
    }

    @Test
    void unhintedSideIsDeliberatelyLeftOut() {
        assertThat(RuntimeHintsPredicates.reflection().onType(UnregisteredPlugin.class)).rejects(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("diagnostics/unregistered.txt")).rejects(hints);
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(Farewell.class)).rejects(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(UnhintedPayload.class)).rejects(hints);
    }
}
