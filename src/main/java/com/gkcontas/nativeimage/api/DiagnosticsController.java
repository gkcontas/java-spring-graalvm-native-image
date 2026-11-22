package com.gkcontas.nativeimage.api;

import com.gkcontas.nativeimage.diagnostics.ClosedWorldProbes;
import com.gkcontas.nativeimage.diagnostics.Diagnostics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiagnosticsController {

    private final ClosedWorldProbes probes;

    public DiagnosticsController(ClosedWorldProbes probes) {
        this.probes = probes;
    }

    @GetMapping("/api/diagnostics")
    public Diagnostics diagnostics() {
        return probes.run();
    }
}
