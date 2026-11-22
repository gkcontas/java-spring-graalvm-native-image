package com.gkcontas.nativeimage.product;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts product creations. Registered only when {@code app.audit.enabled=true}
 * (see {@link AuditConfiguration}) — a condition Spring AOT evaluates at build
 * time, so on a native image the runtime value of the property no longer matters.
 */
public class AuditTrail {

    private final AtomicLong created = new AtomicLong();

    public void productCreated() {
        created.incrementAndGet();
    }

    public long created() {
        return created.get();
    }
}
