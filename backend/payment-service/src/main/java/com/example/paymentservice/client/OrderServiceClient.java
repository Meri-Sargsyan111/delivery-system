package com.example.paymentservice.client;

import com.example.paymentservice.client.dto.CreateOrderFromPaymentPayload;
import com.example.paymentservice.client.dto.OrderCreationResult;
import com.example.paymentservice.exception.OrderCreationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Calls order-service's internal, shared-secret-protected endpoint to create an order
 * once a payment is verified SUCCEEDED. No JWT is available on this call path (it can
 * originate from a provider webhook with no request context at all), so this
 * authenticates via X-Internal-Token instead - same shared secret order-service already
 * presents to courier-service's reserve endpoint (see application.yml's
 * internal.service-token). Retries inline as a fast-path optimization only - the
 * reconciliation sweep (see OrderCreationReconciliationJob) is the actual reliability
 * backstop if every retry here is exhausted or payment-service crashes mid-flow.
 */
@Slf4j
@Component
public class OrderServiceClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String PATH = "/orders/internal/from-payment";

    private final RestTemplate restTemplate;
    private final String orderServiceBaseUrl;
    private final String internalServiceToken;
    private final int retryAttempts;
    private final long retryBackoffMs;

    public OrderServiceClient(RestTemplate restTemplate,
                               @Value("${services.order-service.base-url}") String orderServiceBaseUrl,
                               @Value("${internal.service-token}") String internalServiceToken,
                               @Value("${payment.order-creation.inline-retry-attempts:3}") int retryAttempts,
                               @Value("${payment.order-creation.inline-retry-backoff-ms:300}") long retryBackoffMs) {
        this.restTemplate = restTemplate;
        this.orderServiceBaseUrl = orderServiceBaseUrl;
        this.internalServiceToken = internalServiceToken;
        this.retryAttempts = retryAttempts;
        this.retryBackoffMs = retryBackoffMs;
    }

    /**
     * @throws OrderCreationException after exhausting all retry attempts - the caller
     *         must not let this propagate as a request failure for a payment that already
     *         succeeded (see class javadoc).
     */
    public Long createOrder(CreateOrderFromPaymentPayload payload) {
        String url = orderServiceBaseUrl + PATH;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INTERNAL_TOKEN_HEADER, internalServiceToken);
        HttpEntity<CreateOrderFromPaymentPayload> request = new HttpEntity<>(payload, headers);

        Exception lastError = null;
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                OrderCreationResult result = restTemplate.postForObject(url, request, OrderCreationResult.class);
                if (result == null || result.id() == null) {
                    throw new OrderCreationException("order-service returned an empty response", null);
                }
                log.info("Order {} created for paymentId={} (attempt {}/{})", result.id(), payload.paymentId(), attempt, retryAttempts);
                return result.id();
            } catch (HttpStatusCodeException | ResourceAccessException ex) {
                lastError = ex;
                log.warn("Order creation attempt {}/{} failed for paymentId={}: {}",
                        attempt, retryAttempts, payload.paymentId(), ex.getMessage());
                if (attempt < retryAttempts) {
                    sleep(retryBackoffMs * attempt);
                }
            }
        }

        throw new OrderCreationException(
                "Failed to create order for paymentId=" + payload.paymentId() + " after " + retryAttempts + " attempts",
                lastError);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
