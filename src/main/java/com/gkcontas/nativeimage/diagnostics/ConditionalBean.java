package com.gkcontas.nativeimage.diagnostics;

/**
 * What the environment says about {@code app.audit.enabled} now, and whether
 * the bean guarded by that property exists. On the JVM they always agree; with
 * AOT (native or {@code -Dspring.aot.enabled=true}) the bean reflects the value
 * at build time.
 */
public record ConditionalBean(String propertyAtRuntime, boolean beanPresent, boolean consistent) {
}
