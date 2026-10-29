package ch.example.orderservice.domain;

import java.util.Set;

/**
 * Lifecycle of an order and the allowed transitions between states.
 *
 * <pre>
 *   CREATED ──▶ PROCESSING ──▶ PROCESSED
 *                     │
 *                     └────────▶ FAILED
 * </pre>
 *
 * CREATED    - persisted after the REST call, before any async work.
 * PROCESSING - the consumer has picked up the order.created event and started working.
 * PROCESSED  - business processing finished successfully.
 * FAILED     - processing failed permanently (after retries / moved to DLQ).
 */
public enum OrderStatus {
    CREATED,
    PROCESSING,
    PROCESSED,
    FAILED;

    static {
        CREATED.allowed = Set.of(PROCESSING);
        PROCESSING.allowed = Set.of(PROCESSED, FAILED);
        PROCESSED.allowed = Set.of();          // terminal state
        FAILED.allowed = Set.of(PROCESSING);   // a retry may move it back to PROCESSING
    }

    private Set<OrderStatus> allowed;

    public boolean canTransitionTo(OrderStatus target) {
        return allowed.contains(target);
    }
}


