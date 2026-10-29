package ch.example.orderservice.messaging;

import ch.example.orderservice.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Consumes {@link OrderCreatedEvent}s from {@code order.created} and runs the
 * (simulated) business processing.
 *
 * <p>Flow:
 * <ol>
 *   <li>start processing (CREATED → PROCESSING) in its own tx</li>
 *   <li>simulate work (logging + small delay) outside any DB tx</li>
 *   <li>complete processing (PROCESSING → PROCESSED) in its own tx</li>
 * </ol>
 *
 * <p>Error handling: the listener runs on a transacted JMS session. If it throws, the
 * message is redelivered; after the configured max attempts Artemis routes it to the DLQ.
 * A permanent/business failure is turned into an explicit FAILED state instead of endless
 * redelivery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedConsumer {

    private final OrderProcessingService processingService;

    @JmsListener(destination = JmsConfig.ORDER_CREATED_QUEUE, containerFactory = "jmsListenerContainerFactory")
    public void onMessage(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent eventId={} orderId={}", event.eventId(), event.orderId());

        boolean shouldProcess = processingService.startProcessing(event.orderId());
        if (!shouldProcess) {
            return; // already processed (idempotent redelivery)
        }

        try {
            simulateWork(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Transient: let the JMS tx roll back so the message is redelivered.
            throw new IllegalStateException("Processing interrupted for order " + event.orderId(), e);
        }

        processingService.completeProcessing(event.orderId());
    }

    private void simulateWork(OrderCreatedEvent event) throws InterruptedException {
        long delayMs = ThreadLocalRandom.current().nextLong(200, 800);
        log.info("Processing order {} (simulated work {} ms)...", event.orderId(), delayMs);
        Thread.sleep(delayMs);
    }
}

