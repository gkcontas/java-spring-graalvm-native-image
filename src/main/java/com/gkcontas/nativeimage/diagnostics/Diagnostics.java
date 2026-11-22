package com.gkcontas.nativeimage.diagnostics;

import java.util.List;

public record Diagnostics(Mode mode, List<Probe> probes, ConditionalBean auditTrail, String greeting) {
}
