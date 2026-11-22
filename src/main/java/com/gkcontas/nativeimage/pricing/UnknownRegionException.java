package com.gkcontas.nativeimage.pricing;

public class UnknownRegionException extends RuntimeException {

    public UnknownRegionException(String region) {
        super("unknown region " + region);
    }
}
