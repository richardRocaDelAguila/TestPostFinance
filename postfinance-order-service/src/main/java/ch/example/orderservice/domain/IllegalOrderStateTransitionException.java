package ch.example.orderservice.domain;

import java.util.UUID;

/**
 * Thrown when an order is asked to perform a transition that the state machine forbids.
 */
public class IllegalOrderStateTransitionException extends RuntimeException {

    public IllegalOrderStateTransitionException(UUID orderId, OrderStatus from, OrderStatus to) {
        super("Illegal state transition for order %s: %s -> %s".formatted(orderId, from, to));
    }
}

