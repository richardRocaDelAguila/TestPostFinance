package ch.example.orderservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends {@link OrderCreatedEvent}s to the broker.
 *
 * <p>Deliberately dumb: it only knows how to put a message on a queue. The decision
 * of <em>when</em> to send (after the DB commit) lives in {@link OrderEventRelay}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final JmsTemplate jmsTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent eventId={} orderId={}", event.eventId(), event.orderId());
        jmsTemplate.convertAndSend(JmsConfig.ORDER_CREATED_QUEUE, event, message -> {
            // Correlation id helps tracing a single order across logs/systems.
            message.setStringProperty("orderId", event.orderId().toString());
            message.setStringProperty("eventId", event.eventId().toString());
            return message;
        });
    }
}

