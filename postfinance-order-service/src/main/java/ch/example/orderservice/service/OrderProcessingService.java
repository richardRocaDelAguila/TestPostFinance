package ch.example.orderservice.service;

import ch.example.orderservice.domain.Order;
import ch.example.orderservice.domain.OrderStatus;
import ch.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Encapsulates the state transitions driven by the asynchronous processing.
 *
 * <p>Each method is its own short transaction. The consumer orchestrates them and keeps the
 * (simulated) long-running work <em>outside</em> any DB transaction, so we never hold a row
 * lock while "working".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProcessingService {

    private final OrderRepository orderRepository;

    /**
     * Moves the order to PROCESSING.
     *
     * @return {@code true} if the order should be processed, {@code false} if it is already
     *         in a terminal/processed state (idempotency guard for redelivered messages).
     */
    @Transactional
    public boolean startProcessing(UUID orderId) {
        Order order = load(orderId);
        if (order.getStatus() == OrderStatus.PROCESSED) {
            log.info("Order {} already PROCESSED, skipping (idempotent redelivery).", orderId);
            return false;
        }
        if (order.getStatus() == OrderStatus.PROCESSING) {
            log.info("Order {} already PROCESSING, continuing.", orderId);
            return true;
        }
        order.markProcessing();
        return true;
    }

    @Transactional
    public void completeProcessing(UUID orderId) {
        Order order = load(orderId);
        if (order.getStatus() == OrderStatus.PROCESSED) {
            return; // idempotent
        }
        order.markProcessed();
        log.info("Order {} marked PROCESSED.", orderId);
    }

    @Transactional
    public void failProcessing(UUID orderId, String reason) {
        Order order = load(orderId);
        order.markFailed(reason);
        log.warn("Order {} marked FAILED: {}", orderId, reason);
    }

    private Order load(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}

