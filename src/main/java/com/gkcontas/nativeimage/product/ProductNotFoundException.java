package com.gkcontas.nativeimage.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long id) {
        super("product " + id + " not found");
    }
}
