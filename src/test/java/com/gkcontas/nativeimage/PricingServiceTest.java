package com.gkcontas.nativeimage.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void instantiatesTheStrategyNamedInConfiguration() {
        assertThat(PricingService.instantiate(HalfUpRounding.class.getName())).isInstanceOf(HalfUpRounding.class);
    }

    @Test
    void unknownStrategyFailsWithAHintAboutReflection() {
        assertThatThrownBy(() -> PricingService.instantiate("com.example.Missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("registered for reflection");
    }

    @Test
    void classThatIsNotAStrategyIsRejected() {
        assertThatThrownBy(() -> PricingService.instantiate(String.class.getName()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("java.lang.String is not a RoundingStrategy");
    }

    @Test
    void halfUpAndHalfEvenDisagreeOnlyOnTies() {
        PricingService halfUp = new PricingService(mapper, HalfUpRounding.class.getName());
        PricingService halfEven = new PricingService(mapper, HalfEvenRounding.class.getName());

        assertThat(halfUp.quote(new BigDecimal("0.25"), "SP").tax()).isEqualByComparingTo("0.05");
        assertThat(halfEven.quote(new BigDecimal("0.25"), "SP").tax()).isEqualByComparingTo("0.04");
        assertThat(halfUp.quote(new BigDecimal("10.05"), "SP").tax())
                .isEqualByComparingTo(halfEven.quote(new BigDecimal("10.05"), "SP").tax());
    }

    @Test
    void readsEveryRegionFromTheResource() {
        TaxTable table = PricingService.load(mapper);

        assertThat(table.version()).isEqualTo("2026-11");
        assertThat(table.rates()).containsOnlyKeys("SP", "RJ", "MG", "PR", "RS");
    }
}
