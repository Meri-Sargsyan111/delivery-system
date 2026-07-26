package com.example.paymentservice.service;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.dto.PaymentDetailsResponse;
import com.example.paymentservice.dto.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {

    /**
     * Idempotent on (customerId, idempotencyKey) - a repeated request with the same key
     * returns the original payment rather than creating a duplicate. Claims the
     * authoritative amount from ai-service's frozen estimate (see AiServiceClient) -
     * never trusts a client-supplied amount.
     */
    PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey);

    /**
     * Re-checks the payment's status directly with the provider (never trusts a client
     * claim) and, if newly observed SUCCEEDED, triggers order creation.
     */
    PaymentResponse verifyPayment(UUID paymentId);

    /**
     * Provider webhook entry point - signature-verified, deduped, and (for a success
     * event) re-verified server-side before being trusted, same as verifyPayment. No
     * caller identity/ownership check here: providers call this directly, authenticated
     * only by their signature.
     */
    void handleWebhook(String provider, String rawPayload, String signatureHeader);

    /** Ownership-checked: CUSTOMER sees only their own payment, ADMIN sees any. */
    PaymentDetailsResponse getDetails(UUID paymentId);

    /** ADMIN sees all payments, CUSTOMER sees only their own. */
    Page<PaymentDetailsResponse> getHistory(Pageable pageable);

    /**
     * No caller-identity check here by design - HTTP callers are gated by
     * @PreAuthorize("hasRole('ADMIN')") on the controller; this is also called directly
     * from DeliveryUpdateConsumer (order cancellation -> auto-refund), which has no
     * SecurityContext to check against at all.
     */
    PaymentDetailsResponse refund(UUID paymentId, BigDecimal amount, String reason);

    /**
     * The reliability backstop for payment->order-creation: sweeps payments stuck
     * SUCCEEDED with no order yet (payment-service crashed mid-flow, or every inline
     * retry was exhausted) and retries via the same atomic claim used everywhere else.
     * Called on a fixed schedule - see service.impl.OrderCreationReconciliationJob.
     */
    void reconcileStuckPayments();
}
