package com.gkcontas.nativeimage.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * On the JVM the condition is evaluated at startup, so the property decides.
 * With AOT the generated bean registrations already contain (or omit) the bean,
 * and this flexibility is gone — the README shows it on the packaged jar.
 */
class AuditConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(AuditConfiguration.class);

    @Test
    void beanExistsWhenThePropertyIsOn() {
        runner.withPropertyValues("app.audit.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(AuditTrail.class));
    }

    @Test
    void beanIsAbsentByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(AuditTrail.class));
    }
}
