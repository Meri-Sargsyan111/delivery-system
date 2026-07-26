package com.example.paymentservice.service.impl;

import com.example.paymentservice.client.AiServiceClient;
import com.example.paymentservice.client.OrderServiceClient;
import com.example.paymentservice.client.dto.CreateOrderFromPaymentPayload;
import com.example.paymentservice.client.dto.EstimateClaimResult;
import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.dto.PaymentDetailsResponse;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentAuditLog;
import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.entity.ProcessedWebhookEvent;
import com.example.paymentservice.event.PaymentLifecycleEvent;
import com.example.paymentservice.exception.EntityNotFoundException;
import com.example.paymentservice.exception.IdempotencyKeyRequiredException;
import com.example.paymentservice.exception.OrderCreationException;
import com.example.paymentservice.exception.PaymentValidationException;
import com.example.paymentservice.provider.PaymentProviderAdapter;
import com.example.paymentservice.provider.PaymentProviderFactory;
import com.example.paymentservice.provider.dto.PaymentCreationContext;
import com.example.paymentservice.provider.dto.ProviderPaymentResult;
import com.example.paymentservice.provider.dto.ProviderVerificationResult;
import com.example.paymentservice.provider.dto.RefundResult;
import com.example.paymentservice.provider.dto.WebhookEvent;
import com.example.paymentservice.repository.PaymentAuditLogRepository;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.ProcessedWebhookEventRepository;
import com.example.paymentservice.security.CurrentUser;
import com.example.paymentservice.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Deliberately has NO @Transactional on any method here: this codebase's convention
 * (see OrderServiceImpl) is per-repository-call atomicity rather than a broad
 * service-level transaction boundary, and that matters more here than usual - several
 * methods make external HTTP calls (to ai-service, order-service, or the payment
 * provider itself) interleaved with DB writes, and holding a DB transaction/connection
 * open across those calls would be a real anti-pattern (connection pool exhaustion,
 * long-held locks for the duration of a network round-trip).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final ProcessedWebhookEventRepository webhookEventRepository;
    private final PaymentProviderFactory providerFactory;
    private final AiServiceClient aiServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final CurrentUser currentUser;
    private final KafkaTemplate<String, PaymentLifecycleEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.order-creation.max-attempts-before-manual-review:20}")
    private int maxAttemptsBeforeManualReview;

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }

        UUID customerId = resolveCustomerId(request);

        Payment existing = paymentRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey).orElse(null);
        if (existing != null) {
            log.info("Idempotent replay of Idempotency-Key for customerId={} - returning existing payment {}",
                    customerId, existing.getId());
            return toResponse(existing);
        }

        EstimateClaimResult claim = aiServiceClient.claimEstimate(request.getEstimateId());

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setTransactionId(payment.getId().toString());
        payment.setCustomerId(customerId);
        payment.setEstimateId(claim.estimateId());
        payment.setAmount(claim.amount());
        payment.setCurrency(claim.currency());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setProvider(request.getPaymentMethod().getProvider());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setOrderDetailsJson(serializeOrderDetails(claim));
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        audit(payment.getId(), "CREATED", "amount=" + claim.amount() + " " + claim.currency()
                + ", provider=" + payment.getProvider() + ", method=" + payment.getPaymentMethod());

        PaymentProviderAdapter adapter = providerFactory.get(payment.getProvider());
        ProviderPaymentResult result = adapter.createPayment(new PaymentCreationContext(
                payment.getId(), payment.getAmount(), payment.getCurrency(), "DeliveryOS order payment"));

        payment.setProviderReference(result.providerReference());
        payment.setProviderResponse(result.rawResponseJson());
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        publishLifecycleEvent(payment);
        log.info("Payment {} created for customerId={}, amount={} {}, provider={}",
                payment.getId(), customerId, payment.getAmount(), payment.getCurrency(), payment.getProvider());

        return toResponseWithClientSecret(payment, result.clientSecret());
    }

    @Override
    public PaymentResponse verifyPayment(UUID paymentId) {
        Payment payment = getOwnedPayment(paymentId);

        PaymentProviderAdapter adapter = providerFactory.get(payment.getProvider());
        ProviderVerificationResult result = adapter.verifyPayment(payment.getProviderReference());

        applyVerificationResult(payment, result);
        paymentRepository.save(payment);
        audit(payment.getId(), "VERIFIED", "status=" + payment.getStatus());

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            publishLifecycleEvent(payment);
            tryCreateOrderForPayment(payment);
        }

        return toResponse(payment);
    }

    @Override
    public void handleWebhook(String providerName, String rawPayload, String signatureHeader) {
        PaymentProvider provider = parseProvider(providerName);
        PaymentProviderAdapter adapter = providerFactory.get(provider);

        if (!adapter.verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("SECURITY: webhook signature verification failed for provider={}", provider);
            throw new PaymentValidationException("Invalid webhook signature");
        }

        WebhookEvent event = adapter.parseWebhookEvent(rawPayload);

        if (webhookEventRepository.existsByProviderAndProviderEventId(provider.name(), event.providerEventId())) {
            log.info("Duplicate webhook delivery ignored: provider={}, eventId={}", provider, event.providerEventId());
            return;
        }
        webhookEventRepository.save(new ProcessedWebhookEvent(null, provider.name(), event.providerEventId(), LocalDateTime.now()));

        if (event.mappedStatus() == null || event.providerReference() == null) {
            log.debug("Ignoring webhook event type={} (no mapped status/reference)", event.eventType());
            return;
        }

        Payment payment = paymentRepository.findByProviderReference(event.providerReference()).orElse(null);
        if (payment == null) {
            log.warn("Webhook event references unknown providerReference={} (provider={})", event.providerReference(), provider);
            return;
        }

        audit(payment.getId(), "WEBHOOK_RECEIVED", "eventType=" + event.eventType() + ", mappedStatus=" + event.mappedStatus());

        if (event.mappedStatus() == PaymentStatus.SUCCEEDED) {
            ProviderVerificationResult verification = adapter.verifyPayment(payment.getProviderReference());
            applyVerificationResult(payment, verification);
            paymentRepository.save(payment);

            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                publishLifecycleEvent(payment);
                tryCreateOrderForPayment(payment);
            }
        } else {
            payment.setStatus(event.mappedStatus());
            payment.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            publishLifecycleEvent(payment);
        }
    }

    @Override
    public PaymentDetailsResponse getDetails(UUID paymentId) {
        return toDetailsResponse(getOwnedPayment(paymentId));
    }

    @Override
    public Page<PaymentDetailsResponse> getHistory(Pageable pageable) {
        if (currentUser.isAdmin()) {
            return paymentRepository.findAll(pageable).map(this::toDetailsResponse);
        }
        return paymentRepository.findByCustomerId(currentUser.getUserId(), pageable).map(this::toDetailsResponse);
    }

    @Override
    public PaymentDetailsResponse refund(UUID paymentId, BigDecimal amount, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new PaymentValidationException(
                    "Only a SUCCEEDED payment can be refunded (current status: " + payment.getStatus() + ")");
        }

        PaymentProviderAdapter adapter = providerFactory.get(payment.getProvider());
        RefundResult result = adapter.refund(payment.getProviderReference(), amount);

        payment.setStatus(result.status());
        payment.setProviderResponse(result.rawResponseJson());
        payment.setCompletedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        audit(payment.getId(), "REFUNDED",
                "amount=" + (amount != null ? amount : payment.getAmount()) + ", reason=" + reason);
        publishLifecycleEvent(payment);
        log.info("Payment {} refunded (reason={})", payment.getId(), reason);

        return toDetailsResponse(payment);
    }

    @Override
    public void reconcileStuckPayments() {
        List<Payment> stuck = paymentRepository
                .findByStatusAndOrderIdIsNullAndOrderCreationInProgressFalseAndNeedsManualReviewFalse(PaymentStatus.SUCCEEDED);
        if (stuck.isEmpty()) {
            return;
        }

        log.info("Reconciliation sweep found {} SUCCEEDED payment(s) with no order yet", stuck.size());
        for (Payment payment : stuck) {
            if (payment.getOrderCreationAttempts() >= maxAttemptsBeforeManualReview) {
                payment.setNeedsManualReview(true);
                paymentRepository.save(payment);
                log.error("Payment {} exceeded max order-creation attempts ({}) - flagged for manual review",
                        payment.getId(), payment.getOrderCreationAttempts());
                audit(payment.getId(), "NEEDS_MANUAL_REVIEW", "orderCreationAttempts=" + payment.getOrderCreationAttempts());
                continue;
            }
            tryCreateOrderForPayment(payment);
        }
    }

    /**
     * Atomic claim (see PaymentRepository.claimForOrderCreation) then a best-effort
     * inline retry via OrderServiceClient - a fast-path only. If every retry is
     * exhausted, the claim is released and the reconciliation sweep
     * (OrderCreationReconciliationJob) becomes the backstop; this method deliberately
     * never lets an order-creation failure propagate as an error to its caller (a
     * webhook handler must not make Stripe re-deliver indefinitely for a payment that
     * already succeeded).
     */
    private void tryCreateOrderForPayment(Payment payment) {
        if (payment.getOrderId() != null) {
            return;
        }

        int claimed = paymentRepository.claimForOrderCreation(payment.getId());
        if (claimed == 0) {
            log.info("Payment {} order creation already claimed or already has an order, skipping", payment.getId());
            return;
        }
        payment.setOrderCreationInProgress(true);

        try {
            OrderDetails details = deserializeOrderDetails(payment.getOrderDetailsJson());
            Long orderId = orderServiceClient.createOrder(new CreateOrderFromPaymentPayload(
                    payment.getCustomerId(), payment.getId(), details.fromAddress(), details.toAddress(),
                    details.packageDescription(), details.weightKg()));

            payment.setOrderId(orderId);
            paymentRepository.save(payment);
            audit(payment.getId(), "ORDER_CREATED", "orderId=" + orderId);
            log.info("Order {} created for payment {}", orderId, payment.getId());
        } catch (OrderCreationException ex) {
            log.error("Order creation failed for payment {} after inline retries - releasing claim, " +
                    "reconciliation sweep will retry", payment.getId(), ex);
            payment.setOrderCreationInProgress(false);
            payment.setOrderCreationAttempts(payment.getOrderCreationAttempts() + 1);
            paymentRepository.save(payment);
            audit(payment.getId(), "ORDER_CREATION_FAILED", ex.getMessage());
        }
    }

    private UUID resolveCustomerId(CreatePaymentRequest request) {
        if (currentUser.isAdmin()) {
            if (request.getCustomerId() == null) {
                throw new IllegalArgumentException("customerId is required when creating a payment as ADMIN");
            }
            return request.getCustomerId();
        }
        return currentUser.getUserId();
    }

    private Payment getOwnedPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        if (!currentUser.isAdmin() && !payment.getCustomerId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Not authorized to access payment " + paymentId);
        }
        return payment;
    }

    /**
     * Amount/currency integrity check: the provider's own confirmed amount must match
     * what we expected to charge. A mismatch is a security violation, not a normal
     * failure - the payment is rejected (FAILED) rather than silently accepted at
     * whatever amount the provider happened to report.
     */
    private void applyVerificationResult(Payment payment, ProviderVerificationResult result) {
        if (result.amount() != null && payment.getAmount().compareTo(result.amount()) != 0) {
            log.error("SECURITY: amount mismatch for payment {}: expected={} {}, provider confirmed={} {}",
                    payment.getId(), payment.getAmount(), payment.getCurrency(), result.amount(), result.currency());
            audit(payment.getId(), "SECURITY_VIOLATION", "Amount mismatch: expected=" + payment.getAmount()
                    + ", providerConfirmed=" + result.amount());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Amount mismatch with provider-confirmed amount");
            payment.setCompletedAt(LocalDateTime.now());
            return;
        }

        payment.setStatus(result.status());
        payment.setProviderResponse(result.rawResponseJson());
        if (isTerminal(result.status())) {
            payment.setCompletedAt(LocalDateTime.now());
        }
        if (result.failureReason() != null) {
            payment.setFailureReason(result.failureReason());
        }
    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED || status == PaymentStatus.REFUNDED
                || status == PaymentStatus.EXPIRED;
    }

    private void publishLifecycleEvent(Payment payment) {
        try {
            kafkaTemplate.send("payment-events", new PaymentLifecycleEvent(
                    payment.getId(), payment.getOrderId(), payment.getCustomerId(), payment.getStatus().name(),
                    payment.getAmount(), payment.getCurrency(), LocalDateTime.now()));
        } catch (Exception e) {
            log.error("Failed to publish payment lifecycle event for payment {} status {}",
                    payment.getId(), payment.getStatus(), e);
        }
    }

    private void audit(UUID paymentId, String eventType, String details) {
        auditLogRepository.save(new PaymentAuditLog(null, paymentId, eventType, details, LocalDateTime.now()));
    }

    private PaymentProvider parseProvider(String providerName) {
        try {
            return PaymentProvider.valueOf(providerName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new PaymentValidationException("Unknown payment provider: " + providerName);
        }
    }

    private String serializeOrderDetails(EstimateClaimResult claim) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("fromAddress", claim.fromAddress());
            payload.put("toAddress", claim.toAddress());
            payload.put("packageDescription", claim.packageDescription());
            payload.put("weightKg", claim.weightKg());

            java.util.Map<String, Object> envelope = new java.util.HashMap<>();
            envelope.put("schemaVersion", 1);
            envelope.put("payload", payload);

            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize order details for estimate " + claim.estimateId(), e);
        }
    }

    private OrderDetails deserializeOrderDetails(String json) {
        try {
            JsonNode envelope = objectMapper.readTree(json);
            JsonNode payload = envelope.get("payload");
            return new OrderDetails(
                    payload.get("fromAddress").asText(),
                    payload.get("toAddress").asText(),
                    payload.hasNonNull("packageDescription") ? payload.get("packageDescription").asText() : null,
                    payload.get("weightKg").asDouble());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize order details", e);
        }
    }

    private record OrderDetails(String fromAddress, String toAddress, String packageDescription, Double weightKg) {}

    private PaymentResponse toResponseWithClientSecret(Payment payment, String clientSecret) {
        return new PaymentResponse(payment.getId(), payment.getStatus(), payment.getAmount(), payment.getCurrency(),
                payment.getProvider(), payment.getPaymentMethod(), clientSecret, payment.getOrderId(), payment.getCreatedAt());
    }

    private PaymentResponse toResponse(Payment payment) {
        return toResponseWithClientSecret(payment, null);
    }

    private PaymentDetailsResponse toDetailsResponse(Payment payment) {
        return new PaymentDetailsResponse(payment.getId(), payment.getTransactionId(), payment.getProvider(),
                payment.getPaymentMethod(), payment.getStatus(), payment.getCurrency(), payment.getAmount(),
                payment.getCustomerId(), payment.getOrderId(), payment.getProviderReference(),
                payment.getCreatedAt(), payment.getCompletedAt(), payment.getFailureReason());
    }
}
