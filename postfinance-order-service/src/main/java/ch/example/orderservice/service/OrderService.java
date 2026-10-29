package ch.example.orderservice.service;

import ch.example.orderservice.api.dto.CreateOrderRequest;
import ch.example.orderservice.domain.Order;
import ch.example.orderservice.messaging.OrderCreatedEvent;
import ch.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for the order use cases. Owns the transaction boundary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates and persists an order with status CREATED, then registers an
     * {@link OrderCreatedEvent} to be sent to JMS <em>after</em> this transaction commits
     * (see {@code OrderEventRelay}).
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = Order.create(request.customerId(), request.amount());
        order = orderRepository.save(order);
        log.info("Created order id={} customerId={} amount={}",
                order.getId(), order.getCustomerId(), order.getAmount());

        eventPublisher.publishEvent(
                OrderCreatedEvent.of(order.getId(), order.getCustomerId(), order.getAmount()));
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}

