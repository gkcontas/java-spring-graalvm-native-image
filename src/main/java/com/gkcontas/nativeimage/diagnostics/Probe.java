package com.gkcontas.nativeimage.diagnostics;

/** The same operation tried with a hint and without one. */
public record Probe(String category, Outcome hinted, Outcome unhinted) {
}
