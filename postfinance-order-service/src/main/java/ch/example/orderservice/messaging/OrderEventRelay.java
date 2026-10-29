package ch.example.orderservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the in-process Spring event to the JMS broker.
 *
 * <p><strong>Why AFTER_COMMIT?</strong> Persisting the order and sending the JMS message are
 * two different resources (DB + broker). Without care this is a classic dual-write problem:
 * if we send the message inside the transaction and the DB then rolls back, we have emitted
 * an event for an order that does not exist.
 *
 * <p>By publishing a Spring {@link OrderCreatedEvent} and reacting only {@code AFTER_COMMIT},
 * we guarantee the message is sent exclusively for orders that were actually committed.
 * The remaining edge case (commit succeeds, broker send fails) is documented in the README;
 * the production-grade fix is the Transactional Outbox pattern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventRelay {

    private final OrderEventPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            publisher.publishOrderCreated(event);
        } catch (RuntimeException ex) {
            // The order is safely committed; losing the event here would strand it in CREATED.
            // In production this is exactly what the outbox pattern prevents. For the task we
            // log loudly so it is observable and could be picked up by a reconciliation job.
            log.error("Failed to publish OrderCreatedEvent for orderId={}. Order remains CREATED "
                    + "and must be reconciled.", event.orderId(), ex);
        }
    }
}

