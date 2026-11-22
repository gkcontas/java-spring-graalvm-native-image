package com.gkcontas.nativeimage.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "sku is required")
        @Pattern(regexp = "[A-Z0-9-]{3,32}", message = "sku must be 3 to 32 uppercase letters, digits or dashes")
        String sku,

        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must have at most 120 characters")
        String name,

        @NotBlank(message = "category is required")
        @Size(max = 40, message = "category must have at most 40 characters")
        String category,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 10 integer digits and 2 decimals")
        BigDecimal price) {
}
