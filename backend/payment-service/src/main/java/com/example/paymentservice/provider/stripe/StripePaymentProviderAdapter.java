package com.example.paymentservice.provider.stripe;

import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.provider.PaymentProviderAdapter;
import com.example.paymentservice.provider.dto.PaymentCreationContext;
import com.example.paymentservice.provider.dto.ProviderPaymentResult;
import com.example.paymentservice.provider.dto.ProviderVerificationResult;
import com.example.paymentservice.provider.dto.RefundResult;
import com.example.paymentservice.provider.dto.WebhookEvent;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Real integration against the official Stripe Java SDK - backs both VISA and
 * MASTERCARD (Stripe auto-detects the card brand from the card number; VISA/MASTERCARD
 * here are the customer's stated intent, not a Stripe-side distinction). Test/sandbox
 * credentials only - see application.yml's payment.providers.stripe block and this
 * project's final report for the known Armenia/AMD production limitation.
 *
 * Amounts are converted to Stripe's minor-unit convention (e.g. cents) via a flat x100 -
 * correct for the vast majority of currencies Stripe actually supports; AMD isn't one of
 * Stripe's officially supported settlement currencies at all, so this is already
 * operating outside Stripe's real support matrix in sandbox/test mode, per the
 * user-confirmed tradeoff.
 */
@Slf4j
@Component
public class StripePaymentProviderAdapter implements PaymentProviderAdapter {

    private static final BigDecimal MINOR_UNIT_SCALE = BigDecimal.valueOf(100);

    private final String webhookSecret;

    public StripePaymentProviderAdapter(
            @Value("${payment.providers.stripe.secret-key:}") String secretKey,
            @Value("${payment.providers.stripe.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    @PostConstruct
    void logConfigurationState() {
        if (Stripe.apiKey == null || Stripe.apiKey.isBlank()) {
            log.warn("Stripe secret key is not configured (STRIPE_SECRET_KEY) - Stripe-backed payments will fail until it is set");
        }
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public ProviderPaymentResult createPayment(PaymentCreationContext context) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toMinorUnits(context.amount()))
                    .setCurrency(context.currency().toLowerCase())
                    .setDescription(context.description())
                    .putMetadata("paymentId", context.paymentId().toString())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Stripe PaymentIntent created: {} for paymentId={}", intent.getId(), context.paymentId());

            return new ProviderPaymentResult(intent.getId(), intent.getClientSecret(), intent.toJson());
        } catch (StripeException e) {
            log.error("Stripe PaymentIntent creation failed for paymentId={}", context.paymentId(), e);
            throw new StripeAdapterException("Failed to create Stripe payment: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderVerificationResult verifyPayment(String providerReference) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(providerReference);
            PaymentStatus status = mapIntentStatus(intent.getStatus());
            BigDecimal amount = fromMinorUnits(intent.getAmountReceived() != null && intent.getAmountReceived() > 0
                    ? intent.getAmountReceived() : intent.getAmount());
            String failureReason = intent.getLastPaymentError() != null ? intent.getLastPaymentError().getMessage() : null;

            return new ProviderVerificationResult(status, amount, intent.getCurrency(), intent.toJson(), failureReason);
        } catch (StripeException e) {
            log.error("Stripe PaymentIntent verification failed for reference={}", providerReference, e);
            throw new StripeAdapterException("Failed to verify Stripe payment: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResult refund(String providerReference, BigDecimal amount) {
        try {
            RefundCreateParams.Builder builder = RefundCreateParams.builder().setPaymentIntent(providerReference);
            if (amount != null) {
                builder.setAmount(toMinorUnits(amount));
            }

            Refund refund = Refund.create(builder.build());
            PaymentStatus status = mapRefundStatus(refund.getStatus());

            return new RefundResult(refund.getId(), status, refund.toJson());
        } catch (StripeException e) {
            log.error("Stripe refund failed for reference={}", providerReference, e);
            throw new StripeAdapterException("Failed to refund Stripe payment: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        try {
            Webhook.constructEvent(rawPayload, signatureHeader, webhookSecret);
            return true;
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public WebhookEvent parseWebhookEvent(String rawPayload) {
        Event event = com.stripe.net.ApiResource.GSON.fromJson(rawPayload, Event.class);

        String providerReference = event.getDataObjectDeserializer().getObject()
                .map(obj -> obj instanceof PaymentIntent intent ? intent.getId() : null)
                .orElse(null);

        PaymentStatus mappedStatus = switch (event.getType()) {
            case "payment_intent.succeeded" -> PaymentStatus.SUCCEEDED;
            case "payment_intent.payment_failed" -> PaymentStatus.FAILED;
            case "payment_intent.canceled" -> PaymentStatus.CANCELLED;
            case "payment_intent.processing" -> PaymentStatus.PROCESSING;
            case "charge.refunded" -> PaymentStatus.REFUNDED;
            default -> null;
        };

        return new WebhookEvent(event.getId(), providerReference, mappedStatus, event.getType());
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.multiply(MINOR_UNIT_SCALE).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private BigDecimal fromMinorUnits(Long minorUnits) {
        if (minorUnits == null) {
            return null;
        }
        return BigDecimal.valueOf(minorUnits).divide(MINOR_UNIT_SCALE, 2, RoundingMode.HALF_UP);
    }

    private PaymentStatus mapIntentStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "processing" -> PaymentStatus.PROCESSING;
            case "requires_capture" -> PaymentStatus.AUTHORIZED;
            case "requires_payment_method", "requires_confirmation", "requires_action" -> PaymentStatus.PENDING;
            case "canceled" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.FAILED;
        };
    }

    private PaymentStatus mapRefundStatus(String stripeRefundStatus) {
        return switch (stripeRefundStatus) {
            case "succeeded" -> PaymentStatus.REFUNDED;
            case "pending" -> PaymentStatus.PROCESSING;
            default -> PaymentStatus.FAILED;
        };
    }
}
