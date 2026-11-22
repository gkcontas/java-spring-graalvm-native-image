package com.gkcontas.nativeimage.diagnostics;

public record Outcome(boolean ok, String detail) {

    static Outcome ok(String detail) {
        return new Outcome(true, detail);
    }

    static Outcome failed(Throwable error) {
        return new Outcome(false, error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    static Outcome failed(String detail) {
        return new Outcome(false, detail);
    }
}
