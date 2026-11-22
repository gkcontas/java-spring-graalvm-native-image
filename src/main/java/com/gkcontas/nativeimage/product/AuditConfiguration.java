package com.gkcontas.nativeimage.product;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuditConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.audit.enabled", havingValue = "true")
    AuditTrail auditTrail() {
        return new AuditTrail();
    }
}
