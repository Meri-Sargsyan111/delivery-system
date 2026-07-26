package com.example.courierservice.client;

import com.example.courierservice.dto.RemoteOrderView;
import com.example.courierservice.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Synchronous read of an order's current status from order-service, used to gate
 * start/deliver actions with an immediate answer instead of eventual Kafka consistency.
 * Also used to revert an assignment order-service's side when a courier rejects an offer
 * or the offer-timeout sweep gives up on one.
 */
@Slf4j
@Component
public class OrderServiceClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final String orderServiceBaseUrl;
    private final String internalServiceToken;

    public OrderServiceClient(RestTemplate restTemplate,
                               @Value("${services.order-service.base-url}") String orderServiceBaseUrl,
                               @Value("${internal.service-token}") String internalServiceToken) {
        this.restTemplate = restTemplate;
        this.orderServiceBaseUrl = orderServiceBaseUrl;
        this.internalServiceToken = internalServiceToken;
    }

    public RemoteOrderView getOrder(Long orderId) {
        String url = orderServiceBaseUrl + "/orders/" + orderId + "/status";
        try {
            return restTemplate.getForObject(url, RemoteOrderView.class);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new EntityNotFoundException("Order not found with id: " + orderId);
            }
            log.error("order-service returned an error while fetching order {}: {}", orderId, ex.getStatusCode());
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("order-service unreachable while fetching order {}", orderId, ex);
            throw ex;
        }
    }

    /**
     * Reverts an assignment on order-service's side (back to CREATED, courier cleared) -
     * called when a courier rejects an offer or the offer-timeout sweep expires one
     * unanswered. A no-op on order-service's side (not an error) if the order has already
     * moved on for any reason - see OrderServiceImpl.unassignOrder.
     */
    public void unassignOrder(Long orderId, Long courierId) {
        String url = orderServiceBaseUrl + "/orders/internal/" + orderId + "/unassign";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INTERNAL_TOKEN_HEADER, internalServiceToken);
        try {
            restTemplate.exchange(url, HttpMethod.PUT,
                    new HttpEntity<>(Map.of("courierId", courierId), headers), Void.class);
        } catch (HttpStatusCodeException ex) {
            log.error("order-service returned an error unassigning order {} from courier {}: {}",
                    orderId, courierId, ex.getStatusCode());
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("order-service unreachable while unassigning order {} from courier {}", orderId, courierId, ex);
            throw ex;
        }
    }
}
