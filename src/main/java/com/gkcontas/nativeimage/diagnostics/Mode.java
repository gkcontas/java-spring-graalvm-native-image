package com.gkcontas.nativeimage.diagnostics;

public record Mode(boolean nativeImage, boolean aotGeneratedArtifacts, String javaVersion, String vmName) {
}
