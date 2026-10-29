package ch.example.orderservice.messaging;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Business event published to the {@code order.created} queue after an order is persisted.
 *
 * <p>Carries the order id (mandatory, so the consumer can load the aggregate) plus a
 * small amount of context to avoid a round-trip for simple consumers. An {@code eventId}
 * and {@code occurredAt} are included to support idempotency and tracing downstream.
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        BigDecimal amount,
        Instant occurredAt
) implements Serializable {

    public static OrderCreatedEvent of(UUID orderId, String customerId, BigDecimal amount) {
        return new OrderCreatedEvent(UUID.randomUUID(), orderId, customerId, amount, Instant.now());
    }
}

