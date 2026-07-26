package com.example.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One payment attempt for one order-intent. orderId stays null until the order is
 * actually created (only after this payment reaches SUCCEEDED - see
 * PaymentServiceImpl/OrderCreationReconciliationJob). orderDetailsJson is a write-once
 * snapshot of the claimed Estimate (see ai-service's Estimate entity) - everything
 * order-service's create-order logic needs, captured once at payment-creation time since
 * the order doesn't exist yet to hold this itself.
 */
@Data
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_customer_id", columnList = "customerId"),
        @Index(name = "idx_payments_order_id", columnList = "orderId"),
        @Index(name = "idx_payments_idempotency", columnList = "customerId,idempotencyKey", unique = true)
})
public class Payment {

    @Id
    private UUID id;

    private String transactionId;

    @Enumerated(EnumType.STRING)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    private PaymentMethodType paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String currency;
    private BigDecimal amount;

    private UUID customerId;
    private Long orderId;
    private UUID estimateId;

    /** {schemaVersion, payload} - see class javadoc. Write-once after creation. */
    @Column(columnDefinition = "TEXT")
    private String orderDetailsJson;

    @Column(columnDefinition = "TEXT")
    private String providerResponse;

    private String providerReference;

    private String idempotencyKey;

    /** Atomic claim flag for the payment->order-creation critical section - see PaymentRepository.claimForOrderCreation. */
    private boolean orderCreationInProgress;

    private int orderCreationAttempts;

    private boolean needsManualReview;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String failureReason;
}
