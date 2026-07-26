package com.example.trackingservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Synchronous fallback read of an order's ownership from order-service, used only when
 * the local OrderOwnership projection has no record yet for the requested order (Kafka
 * consumer lag, or an event that hasn't arrived at all - see TrackingServiceImpl). Mirrors
 * courier-service's own OrderServiceClient, which reads the same public
 * GET /orders/{id}/status endpoint for the identical reason.
 */
@Slf4j
@Component
public class OrderServiceClient {

    private final RestTemplate restTemplate;
    private final String orderServiceBaseUrl;

    public OrderServiceClient(RestTemplate restTemplate,
                               @Value("${services.order-service.base-url}") String orderServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.orderServiceBaseUrl = orderServiceBaseUrl;
    }

    /**
     * Returns null (never throws) on a 404 or any order-service/network failure - this
     * is a best-effort fallback for an authorization check that must otherwise safely
     * deny, not a path that should turn into a 500 when order-service is slow or down.
     */
    public RemoteOrderView getOrder(Long orderId) {
        String url = orderServiceBaseUrl + "/orders/" + orderId + "/status";
        try {
            return restTemplate.getForObject(url, RemoteOrderView.class);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                log.error("order-service returned an error while fetching order {}: {}", orderId, ex.getStatusCode());
            }
            return null;
        } catch (ResourceAccessException ex) {
            log.error("order-service unreachable while fetching order {}", orderId, ex);
            return null;
        }
    }
}