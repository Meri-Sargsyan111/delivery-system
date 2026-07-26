package com.example.paymentservice.repository;

import com.example.paymentservice.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByProviderReference(String providerReference);

    Page<Payment> findByCustomerId(UUID customerId, Pageable pageable);

    /**
     * Atomic claim for the payment->order-creation critical section: a single
     * conditional UPDATE, not a read-then-write, so the webhook path, the verify path,
     * and the reconciliation sweep can never all attempt to create the same order twice.
     * Returns the number of rows updated - 1 means this call won the claim, 0 means
     * someone else already has it in progress or the order already exists.
     */
    @Modifying
    @Query("UPDATE Payment p SET p.orderCreationInProgress = true WHERE p.id = :id " +
            "AND p.orderId IS NULL AND p.orderCreationInProgress = false")
    int claimForOrderCreation(@Param("id") UUID id);

    /** Backstop for the reconciliation sweep - see service.impl.OrderCreationReconciliationJob. */
    List<Payment> findByStatusAndOrderIdIsNullAndOrderCreationInProgressFalseAndNeedsManualReviewFalse(
            com.example.paymentservice.entity.PaymentStatus status);
}
