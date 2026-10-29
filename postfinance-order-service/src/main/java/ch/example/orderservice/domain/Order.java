package ch.example.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order aggregate. Kept intentionally small for the task, but with the fields
 * you typically want in production: a surrogate UUID key, an optimistic-locking
 * version, and audit timestamps.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    /** Optimistic locking: protects against lost updates when the consumer and an API call race. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "failure_reason")
    private String failureReason;

    public static Order create(String customerId, BigDecimal amount) {
        Order order = new Order();
        order.customerId = customerId;
        order.amount = amount;
        order.status = OrderStatus.CREATED;
        Instant now = Instant.now();
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public void markProcessing() {
        transitionTo(OrderStatus.PROCESSING);
    }

    public void markProcessed() {
        transitionTo(OrderStatus.PROCESSED);
    }

    public void markFailed(String reason) {
        this.failureReason = reason;
        transitionTo(OrderStatus.FAILED);
    }

    /**
     * Guards the state machine so an order cannot jump to an illegal state
     * (e.g. from PROCESSED back to CREATED). Keeps transitions auditable.
     */
    private void transitionTo(OrderStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalOrderStateTransitionException(this.id, this.status, target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }
}

