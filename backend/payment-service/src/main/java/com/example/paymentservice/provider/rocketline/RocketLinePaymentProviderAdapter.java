package com.example.paymentservice.provider.rocketline;

import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.provider.PaymentProviderAdapter;
import com.example.paymentservice.provider.PaymentProviderNotConfiguredException;
import com.example.paymentservice.provider.dto.PaymentCreationContext;
import com.example.paymentservice.provider.dto.ProviderPaymentResult;
import com.example.paymentservice.provider.dto.ProviderVerificationResult;
import com.example.paymentservice.provider.dto.RefundResult;
import com.example.paymentservice.provider.dto.WebhookEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Full interface conformance and wiring for "Rocket Line" as a third payment method -
 * real API documentation for this provider has not been supplied yet, so every method
 * here deliberately throws PaymentProviderNotConfiguredException rather than inventing
 * endpoint paths, request/response shapes, or a signature scheme that don't actually
 * exist ("do not invent custom protocols"). The RestTemplate + config placeholders
 * (rocket-line.base-url/api-key/webhook-secret, all unset by default - see
 * application.yml) are already in place so wiring in the real HTTP calls once docs
 * arrive is a change scoped entirely to this one class - PaymentServiceImpl,
 * PaymentProviderFactory, and every controller/DTO are already provider-agnostic.
 */
@Slf4j
@Component
public class RocketLinePaymentProviderAdapter implements PaymentProviderAdapter {

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String webhookSecret;

    public RocketLinePaymentProviderAdapter(
            RestTemplate restTemplate,
            @Value("${payment.providers.rocket-line.enabled:false}") boolean enabled,
            @Value("${payment.providers.rocket-line.base-url:}") String baseUrl,
            @Value("${payment.providers.rocket-line.api-key:}") String apiKey,
            @Value("${payment.providers.rocket-line.webhook-secret:}") String webhookSecret) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
    }

    @PostConstruct
    void logConfigurationState() {
        if (!enabled) {
            log.warn("Rocket Line payment provider is registered but not configured - " +
                    "awaiting real API documentation. All Rocket Line payment attempts will fail " +
                    "with PaymentProviderNotConfiguredException until payment.providers.rocket-line " +
                    "is enabled and base-url/api-key/webhook-secret are set.");
        }
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.ROCKET_LINE;
    }

    @Override
    public ProviderPaymentResult createPayment(PaymentCreationContext context) {
        throw notConfigured();
    }

    @Override
    public ProviderVerificationResult verifyPayment(String providerReference) {
        throw notConfigured();
    }

    @Override
    public RefundResult refund(String providerReference, BigDecimal amount) {
        throw notConfigured();
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        throw notConfigured();
    }

    @Override
    public WebhookEvent parseWebhookEvent(String rawPayload) {
        throw notConfigured();
    }

    private PaymentProviderNotConfiguredException notConfigured() {
        return new PaymentProviderNotConfiguredException(
                "Rocket Line is not yet configured - real provider API details are required " +
                        "before this payment method can be used (see RocketLinePaymentProviderAdapter)");
    }
}
