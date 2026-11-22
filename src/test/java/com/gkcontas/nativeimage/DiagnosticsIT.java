package com.gkcontas.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.nativeimage.diagnostics.Diagnostics;
import com.gkcontas.nativeimage.diagnostics.Probe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

/**
 * On the JVM the closed world does not exist: the unhinted side of every probe
 * works too. That is the whole trap — the test suite stays green and the
 * failure only appears in the native binary. The hint tests are what make the
 * JVM build catch it.
 */
class DiagnosticsIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate http;

    @Test
    void onTheJvmEveryProbePassesWithOrWithoutHints() {
        Diagnostics d = http.getForObject("/api/diagnostics", Diagnostics.class);

        assertThat(d.mode().nativeImage()).isFalse();
        assertThat(d.mode().aotGeneratedArtifacts()).isFalse();
        assertThat(d.probes()).extracting(Probe::category)
                .containsExactly("reflection", "resource", "jdk-proxy", "jackson");
        assertThat(d.probes()).allSatisfy(p -> {
            assertThat(p.hinted().ok()).as(p.category() + " hinted").isTrue();
            assertThat(p.unhinted().ok()).as(p.category() + " unhinted").isTrue();
        });
        assertThat(d.probes().get(3).unhinted().detail()).isEqualTo("{\"sku\":\"sku-1\",\"quantity\":3}");
    }

    @Test
    void conditionalBeanFollowsThePropertyOnTheJvm() {
        Diagnostics d = http.getForObject("/api/diagnostics", Diagnostics.class);

        assertThat(d.auditTrail().propertyAtRuntime()).isEqualTo("false");
        assertThat(d.auditTrail().beanPresent()).isFalse();
        assertThat(d.auditTrail().consistent()).isTrue();
        assertThat(d.greeting()).isEqualTo("hello");
    }
}
