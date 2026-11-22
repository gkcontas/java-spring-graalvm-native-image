package com.gkcontas.nativeimage.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gkcontas.nativeimage.product.AuditTrail;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.aot.AotDetector;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.NativeDetector;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Runs each kind of dynamic access twice, with and without a hint. Class names
 * come from configuration so the compiler cannot constant-fold them into a
 * direct reference — exactly the situation of a plugin, a driver or a strategy
 * chosen by name in production.
 */
@Component
@ImportRuntimeHints(DiagnosticsHints.class)
@RegisterReflectionForBinding(HintedPayload.class)
public class ClosedWorldProbes {

    private static final String EXPECTED_JSON = "{\"sku\":\"sku-1\",\"quantity\":3}";

    private final ObjectMapper mapper;
    private final Environment environment;
    private final ObjectProvider<AuditTrail> auditTrail;
    private final String registeredClass;
    private final String unregisteredClass;
    private final String greeting;

    public ClosedWorldProbes(ObjectMapper mapper, Environment environment, ObjectProvider<AuditTrail> auditTrail,
                             @Value("${app.diagnostics.registered-class}") String registeredClass,
                             @Value("${app.diagnostics.unregistered-class}") String unregisteredClass,
                             @Value("${app.greeting}") String greeting) {
        this.mapper = mapper;
        this.environment = environment;
        this.auditTrail = auditTrail;
        this.registeredClass = registeredClass;
        this.unregisteredClass = unregisteredClass;
        this.greeting = greeting;
    }

    public Diagnostics run() {
        return new Diagnostics(mode(), probes(), conditional(), greeting);
    }

    public List<Probe> probes() {
        return List.of(
                new Probe("reflection", attempt(() -> instantiate(registeredClass)),
                        attempt(() -> instantiate(unregisteredClass))),
                new Probe("resource", attempt(() -> read(DiagnosticsHints.REGISTERED_RESOURCE)),
                        attempt(() -> read(DiagnosticsHints.UNREGISTERED_RESOURCE))),
                new Probe("jdk-proxy", attempt(() -> proxy(Greeter.class).greet("native")),
                        attempt(() -> proxy(Farewell.class).greet("native"))),
                new Probe("jackson", serialize(new HintedPayload("sku-1", 3)),
                        serialize(new UnhintedPayload("sku-1", 3))));
    }

    public ConditionalBean conditional() {
        String property = environment.getProperty("app.audit.enabled", "false");
        boolean present = auditTrail.getIfAvailable() != null;
        return new ConditionalBean(property, present, Boolean.parseBoolean(property) == present);
    }

    static Mode mode() {
        return new Mode(NativeDetector.inNativeImage(), AotDetector.useGeneratedArtifacts(),
                System.getProperty("java.version"), System.getProperty("java.vm.name"));
    }

    private static Outcome attempt(Supplier<String> action) {
        try {
            return Outcome.ok(action.get());
        } catch (Throwable error) {
            // Throwable on purpose: missing metadata on a native image can surface
            // as an Error subclass, not only as a checked reflection exception.
            return Outcome.failed(error);
        }
    }

    private static String instantiate(String className) {
        try {
            return Class.forName(className).getConstructor().newInstance().toString();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static <T> T proxy(Class<T> type) {
        InvocationHandler handler = (target, method, args) -> method.getName() + " " + args[0] + " via proxy";
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private Outcome serialize(Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            // Without metadata Jackson may not throw: it can quietly write "{}".
            return EXPECTED_JSON.equals(json) ? Outcome.ok(json) : Outcome.failed("wrote " + json);
        } catch (Throwable error) {
            return Outcome.failed(error);
        }
    }
}
