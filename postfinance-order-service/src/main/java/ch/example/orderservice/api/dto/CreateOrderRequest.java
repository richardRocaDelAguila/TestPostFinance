package ch.example.orderservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Incoming payload for POST /orders.
 *
 * <p>Validation is deliberately strict: a blank customer or a non-positive amount
 * is rejected at the edge (400) instead of creating a nonsensical order.
 */
public record CreateOrderRequest(

        @NotBlank(message = "customerId must not be blank")
        String customerId,

        @NotNull(message = "amount must not be null")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount
) {
}

