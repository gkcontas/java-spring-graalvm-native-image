package com.gkcontas.nativeimage.product;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("sku " + sku + " already exists");
    }
}
