package com.example.paymentservice.provider;

import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.provider.dto.PaymentCreationContext;
import com.example.paymentservice.provider.dto.ProviderPaymentResult;
import com.example.paymentservice.provider.dto.ProviderVerificationResult;
import com.example.paymentservice.provider.dto.RefundResult;
import com.example.paymentservice.provider.dto.WebhookEvent;

/**
 * One implementation per real payment processor (see StripePaymentProviderAdapter,
 * RocketLinePaymentProviderAdapter). PaymentServiceImpl never talks to a provider
 * directly - it only goes through this interface via PaymentProviderFactory, so adding a
 * new provider later is a new adapter class + a PaymentMethodType mapping entry, not a
 * change to any business logic.
 */
public interface PaymentProviderAdapter {

    PaymentProvider getProvider();

    ProviderPaymentResult createPayment(PaymentCreationContext context);

    /** Real server-side re-check against the provider - never trust a client/webhook claim alone as the final word. */
    ProviderVerificationResult verifyPayment(String providerReference);

    RefundResult refund(String providerReference, java.math.BigDecimal amount);

    boolean verifyWebhookSignature(String rawPayload, String signatureHeader);

    /** Only call after verifyWebhookSignature has returned true. */
    WebhookEvent parseWebhookEvent(String rawPayload);
}
