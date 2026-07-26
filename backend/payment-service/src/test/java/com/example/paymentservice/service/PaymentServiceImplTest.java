package com.example.paymentservice.service;

import com.example.paymentservice.client.AiServiceClient;
import com.example.paymentservice.client.OrderServiceClient;
import com.example.paymentservice.client.dto.CreateOrderFromPaymentPayload;
import com.example.paymentservice.client.dto.EstimateClaimResult;
import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.dto.PaymentDetailsResponse;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentAuditLog;
import com.example.paymentservice.entity.PaymentMethodType;
import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.entity.ProcessedWebhookEvent;
import com.example.paymentservice.event.PaymentLifecycleEvent;
import com.example.paymentservice.exception.EntityNotFoundException;
import com.example.paymentservice.exception.EstimateClaimException;
import com.example.paymentservice.exception.IdempotencyKeyRequiredException;
import com.example.paymentservice.exception.OrderCreationException;
import com.example.paymentservice.exception.PaymentValidationException;
import com.example.paymentservice.provider.PaymentProviderAdapter;
import com.example.paymentservice.provider.PaymentProviderFactory;
import com.example.paymentservice.provider.dto.ProviderPaymentResult;
import com.example.paymentservice.provider.dto.ProviderVerificationResult;
import com.example.paymentservice.provider.dto.RefundResult;
import com.example.paymentservice.provider.dto.WebhookEvent;
import com.example.paymentservice.repository.PaymentAuditLogRepository;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.ProcessedWebhookEventRepository;
import com.example.paymentservice.security.CurrentUser;
import com.example.paymentservice.service.impl.PaymentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ESTIMATE_ID = UUID.randomUUID();

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAuditLogRepository auditLogRepository;
    @Mock private ProcessedWebhookEventRepository webhookEventRepository;
    @Mock private PaymentProviderFactory providerFactory;
    @Mock private PaymentProviderAdapter stripeAdapter;
    @Mock private AiServiceClient aiServiceClient;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private CurrentUser currentUser;
    @Mock private KafkaTemplate<String, PaymentLifecycleEvent> kafkaTemplate;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() throws Exception {
        paymentService = new PaymentServiceImpl(paymentRepository, auditLogRepository, webhookEventRepository,
                providerFactory, aiServiceClient, orderServiceClient, currentUser, kafkaTemplate, new ObjectMapper());

        var field = PaymentServiceImpl.class.getDeclaredField("maxAttemptsBeforeManualReview");
        field.setAccessible(true);
        field.set(paymentService, 20);

        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(CUSTOMER_ID);
        lenient().when(providerFactory.get(PaymentProvider.STRIPE)).thenReturn(stripeAdapter);
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(auditLogRepository.save(any(PaymentAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private EstimateClaimResult estimateClaim() {
        return new EstimateClaimResult(ESTIMATE_ID, "From St", "To St", "Box", 2.5,
                BigDecimal.valueOf(5000), "AMD");
    }

    @Test
    void createPayment_missingIdempotencyKey_throws() {
        CreatePaymentRequest request = new CreatePaymentRequest(ESTIMATE_ID, PaymentMethodType.VISA, null);

        assertThatThrownBy(() -> paymentService.createPayment(request, null))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
        assertThatThrownBy(() -> paymentService.createPayment(request, "  "))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
    }

    @Test
    void createPayment_success_claimsEstimateAndCreatesProcessingPaymentWithClientSecret() {
        CreatePaymentRequest request = new CreatePaymentRequest(ESTIMATE_ID, PaymentMethodType.VISA, null);
        when(paymentRepository.findByCustomerIdAndIdempotencyKey(CUSTOMER_ID, "key-1")).thenReturn(Optional.empty());
        when(aiServiceClient.claimEstimate(ESTIMATE_ID)).thenReturn(estimateClaim());
        when(stripeAdapter.createPayment(any())).thenReturn(
                new ProviderPaymentResult("pi_123", "secret_abc", "{}"));

        PaymentResponse response = paymentService.createPayment(request, "key-1");

        assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(response.currency()).isEqualTo("AMD");
        assertThat(response.clientSecret()).isEqualTo("secret_abc");
        assertThat(response.provider()).isEqualTo(PaymentProvider.STRIPE);

        verify(kafkaTemplate).send(eq("payment-events"), any(PaymentLifecycleEvent.class));
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    void createPayment_repeatedIdempotencyKey_returnsExistingPaymentWithoutClaimingEstimateAgain() {
        CreatePaymentRequest request = new CreatePaymentRequest(ESTIMATE_ID, PaymentMethodType.VISA, null);
        Payment existing = existingPayment(PaymentStatus.PROCESSING);
        when(paymentRepository.findByCustomerIdAndIdempotencyKey(CUSTOMER_ID, "key-1")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.createPayment(request, "key-1");

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(aiServiceClient, never()).claimEstimate(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_asAdminWithoutCustomerId_throws() {
        when(currentUser.isAdmin()).thenReturn(true);
        CreatePaymentRequest request = new CreatePaymentRequest(ESTIMATE_ID, PaymentMethodType.VISA, null);

        assertThatThrownBy(() -> paymentService.createPayment(request, "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createPayment_expiredEstimate_propagatesEstimateClaimException() {
        CreatePaymentRequest request = new CreatePaymentRequest(ESTIMATE_ID, PaymentMethodType.VISA, null);
        when(paymentRepository.findByCustomerIdAndIdempotencyKey(CUSTOMER_ID, "key-1")).thenReturn(Optional.empty());
        when(aiServiceClient.claimEstimate(ESTIMATE_ID)).thenThrow(
                new EstimateClaimException(org.springframework.http.HttpStatus.GONE, "Estimate expired"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "key-1"))
                .isInstanceOf(EstimateClaimException.class);
        verify(stripeAdapter, never()).createPayment(any());
    }

    @Test
    void verifyPayment_succeeded_updatesStatusAndTriggersOrderCreation() {
        Payment payment = existingPayment(PaymentStatus.PROCESSING);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(stripeAdapter.verifyPayment("pi_123")).thenReturn(
                new ProviderVerificationResult(PaymentStatus.SUCCEEDED, payment.getAmount(), "AMD", "{}", null));
        when(paymentRepository.claimForOrderCreation(payment.getId())).thenReturn(1);
        when(orderServiceClient.createOrder(any())).thenReturn(42L);

        PaymentResponse response = paymentService.verifyPayment(payment.getId());

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getOrderId()).isEqualTo(42L);
        ArgumentCaptor<CreateOrderFromPaymentPayload> captor = ArgumentCaptor.forClass(CreateOrderFromPaymentPayload.class);
        verify(orderServiceClient).createOrder(captor.capture());
        assertThat(captor.getValue().fromAddress()).isEqualTo("From St");
        verify(kafkaTemplate).send(eq("payment-events"), any(PaymentLifecycleEvent.class));
    }

    @Test
    void verifyPayment_amountMismatch_failsPaymentAsSecurityViolation() {
        Payment payment = existingPayment(PaymentStatus.PROCESSING);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(stripeAdapter.verifyPayment("pi_123")).thenReturn(
                new ProviderVerificationResult(PaymentStatus.SUCCEEDED, BigDecimal.valueOf(1), "AMD", "{}", null));

        PaymentResponse response = paymentService.verifyPayment(payment.getId());

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(orderServiceClient, never()).createOrder(any());
        ArgumentCaptor<PaymentAuditLog> auditCaptor = ArgumentCaptor.forClass(PaymentAuditLog.class);
        verify(auditLogRepository, times(2)).save(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues()).anyMatch(a -> "SECURITY_VIOLATION".equals(a.getEventType()));
    }

    @Test
    void verifyPayment_orderCreationFailsAfterRetries_releasesClaimForSweepToRetry() {
        Payment payment = existingPayment(PaymentStatus.PROCESSING);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(stripeAdapter.verifyPayment("pi_123")).thenReturn(
                new ProviderVerificationResult(PaymentStatus.SUCCEEDED, payment.getAmount(), "AMD", "{}", null));
        when(paymentRepository.claimForOrderCreation(payment.getId())).thenReturn(1);
        when(orderServiceClient.createOrder(any())).thenThrow(new OrderCreationException("boom", null));

        paymentService.verifyPayment(payment.getId());

        assertThat(payment.isOrderCreationInProgress()).isFalse();
        assertThat(payment.getOrderCreationAttempts()).isEqualTo(1);
        assertThat(payment.getOrderId()).isNull();
    }

    @Test
    void verifyPayment_alreadyClaimedByAnotherCaller_doesNotCallOrderService() {
        Payment payment = existingPayment(PaymentStatus.PROCESSING);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(stripeAdapter.verifyPayment("pi_123")).thenReturn(
                new ProviderVerificationResult(PaymentStatus.SUCCEEDED, payment.getAmount(), "AMD", "{}", null));
        when(paymentRepository.claimForOrderCreation(payment.getId())).thenReturn(0);

        paymentService.verifyPayment(payment.getId());

        verify(orderServiceClient, never()).createOrder(any());
    }

    @Test
    void verifyPayment_unownedByNonAdminCustomer_throwsAccessDenied() {
        Payment payment = existingPayment(PaymentStatus.PROCESSING);
        payment.setCustomerId(OTHER_CUSTOMER_ID);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.verifyPayment(payment.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void verifyPayment_asAdmin_canAccessAnyCustomersPayment() {
        when(currentUser.isAdmin()).thenReturn(true);
        Payment payment = existingPayment(PaymentStatus.PROCESSING);
        payment.setCustomerId(OTHER_CUSTOMER_ID);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(stripeAdapter.verifyPayment("pi_123")).thenReturn(
                new ProviderVerificationResult(PaymentStatus.PROCESSING, payment.getAmount(), "AMD", "{}", null));

        PaymentResponse response = paymentService.verifyPayment(payment.getId());

        assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void verifyPayment_unknownPayment_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verifyPayment(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void handleWebhook_invalidSignature_throwsPaymentValidationException() {
        when(stripeAdapter.verifyWebhookSignature(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> paymentService.handleWebhook("stripe", "{}", "bad-sig"))
                .isInstanceOf(PaymentValidationException.class);
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void handleWebhook_duplicateDelivery_isIgnoredSecondTime() {
        when(stripeAdapter.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(stripeAdapter.parseWebhookEvent(anyString())).thenReturn(
                new WebhookEvent("evt_1", "pi_123", PaymentStatus.SUCCEEDED, "payment_intent.succeeded"));
        when(webhookEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_1")).thenReturn(true);

        paymentService.handleWebhook("stripe", "{}", "sig");

        verify(paymentRepository, never()).findByProviderReference(any());
    }

    @Test
    void handleWebhook_succeeded_reVerifiesWithProviderRatherThanTrustingPayloadAlone() {
        Payment payment = existingPayment(PaymentStatus.PROCESSING);
        when(stripeAdapter.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(stripeAdapter.parseWebhookEvent(anyString())).thenReturn(
                new WebhookEvent("evt_1", "pi_123", PaymentStatus.SUCCEEDED, "payment_intent.succeeded"));
        when(webhookEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_1")).thenReturn(false);
        when(paymentRepository.findByProviderReference("pi_123")).thenReturn(Optional.of(payment));
        when(stripeAdapter.verifyPayment("pi_123")).thenReturn(
                new ProviderVerificationResult(PaymentStatus.SUCCEEDED, payment.getAmount(), "AMD", "{}", null));
        when(paymentRepository.claimForOrderCreation(payment.getId())).thenReturn(1);
        when(orderServiceClient.createOrder(any())).thenReturn(99L);

        paymentService.handleWebhook("stripe", "{}", "sig");

        verify(stripeAdapter).verifyPayment("pi_123");
        assertThat(payment.getOrderId()).isEqualTo(99L);
    }

    @Test
    void handleWebhook_unknownProviderReference_isIgnoredSafely() {
        when(stripeAdapter.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(stripeAdapter.parseWebhookEvent(anyString())).thenReturn(
                new WebhookEvent("evt_1", "pi_unknown", PaymentStatus.SUCCEEDED, "payment_intent.succeeded"));
        when(paymentRepository.findByProviderReference("pi_unknown")).thenReturn(Optional.empty());

        paymentService.handleWebhook("stripe", "{}", "sig");

        verify(orderServiceClient, never()).createOrder(any());
    }

    @Test
    void refund_succeededPayment_callsProviderAndPublishesEvent() {
        Payment payment = existingPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(stripeAdapter.refund("pi_123", null)).thenReturn(
                new RefundResult("re_1", PaymentStatus.REFUNDED, "{}"));

        PaymentDetailsResponse response = paymentService.refund(payment.getId(), null, "customer requested");

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        verify(kafkaTemplate).send(eq("payment-events"), any(PaymentLifecycleEvent.class));
    }

    @Test
    void refund_nonSucceededPayment_throwsValidationException() {
        Payment payment = existingPayment(PaymentStatus.PENDING);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(payment.getId(), null, "x"))
                .isInstanceOf(PaymentValidationException.class);
        verify(stripeAdapter, never()).refund(anyString(), any());
    }

    @Test
    void refund_hasNoOwnershipCheck_callableWithoutSecurityContext() {
        Payment payment = existingPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(stripeAdapter.refund("pi_123", null)).thenReturn(new RefundResult("re_1", PaymentStatus.REFUNDED, "{}"));

        assertThat(paymentService.refund(payment.getId(), null, "order cancelled").status())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void reconcileStuckPayments_retriesEachStuckPayment() {
        Payment stuck = existingPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByStatusAndOrderIdIsNullAndOrderCreationInProgressFalseAndNeedsManualReviewFalse(PaymentStatus.SUCCEEDED))
                .thenReturn(List.of(stuck));
        when(paymentRepository.claimForOrderCreation(stuck.getId())).thenReturn(1);
        when(orderServiceClient.createOrder(any())).thenReturn(7L);

        paymentService.reconcileStuckPayments();

        assertThat(stuck.getOrderId()).isEqualTo(7L);
    }

    @Test
    void reconcileStuckPayments_pastMaxAttempts_flagsForManualReviewWithoutRetrying() {
        Payment stuck = existingPayment(PaymentStatus.SUCCEEDED);
        stuck.setOrderCreationAttempts(25);
        when(paymentRepository.findByStatusAndOrderIdIsNullAndOrderCreationInProgressFalseAndNeedsManualReviewFalse(PaymentStatus.SUCCEEDED))
                .thenReturn(List.of(stuck));

        paymentService.reconcileStuckPayments();

        assertThat(stuck.isNeedsManualReview()).isTrue();
        verify(orderServiceClient, never()).createOrder(any());
    }

    @Test
    void reconcileStuckPayments_nothingStuck_doesNothing() {
        when(paymentRepository.findByStatusAndOrderIdIsNullAndOrderCreationInProgressFalseAndNeedsManualReviewFalse(PaymentStatus.SUCCEEDED))
                .thenReturn(List.of());

        paymentService.reconcileStuckPayments();

        verify(orderServiceClient, never()).createOrder(any());
        verify(paymentRepository, never()).save(any());
    }

    private Payment existingPayment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setTransactionId(payment.getId().toString());
        payment.setCustomerId(CUSTOMER_ID);
        payment.setEstimateId(ESTIMATE_ID);
        payment.setAmount(BigDecimal.valueOf(5000));
        payment.setCurrency("AMD");
        payment.setPaymentMethod(PaymentMethodType.VISA);
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setStatus(status);
        payment.setProviderReference("pi_123");
        payment.setIdempotencyKey("key-1");
        payment.setOrderDetailsJson(
                "{\"schemaVersion\":1,\"payload\":{\"fromAddress\":\"From St\",\"toAddress\":\"To St\",\"packageDescription\":\"Box\",\"weightKg\":2.5}}");
        return payment;
    }
}
