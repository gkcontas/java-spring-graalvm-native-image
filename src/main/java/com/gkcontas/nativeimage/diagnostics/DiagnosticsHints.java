package com.gkcontas.nativeimage.diagnostics;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Hints for one side of each probe. The other side is left out on purpose:
 * {@link UnregisteredPlugin}, {@code diagnostics/unregistered.txt},
 * {@link Farewell} and {@link UnhintedPayload}.
 */
public class DiagnosticsHints implements RuntimeHintsRegistrar {

    static final String REGISTERED_RESOURCE = "diagnostics/registered.txt";
    static final String UNREGISTERED_RESOURCE = "diagnostics/unregistered.txt";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(RegisteredPlugin.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        hints.resources().registerPattern(REGISTERED_RESOURCE);
        hints.proxies().registerJdkProxy(Greeter.class);
    }
}
