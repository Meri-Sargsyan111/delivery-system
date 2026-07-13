package com.example.courierservice.client;

import com.example.courierservice.dto.RemoteOrderView;
import com.example.courierservice.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Synchronous read of an order's current status from order-service, used to gate
 * start/deliver actions with an immediate answer instead of eventual Kafka consistency.
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
}
