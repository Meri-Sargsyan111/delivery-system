package com.example.orderservice.client;

import com.example.orderservice.exception.CourierAssignmentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Synchronous call to courier-service to reserve a courier for an order.
 * Assignment needs an immediate accept/reject answer for the dispatcher,
 * so this goes over REST rather than Kafka.
 */
@Slf4j
@Component
public class CourierServiceClient {

    private final RestTemplate restTemplate;
    private final String courierServiceBaseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CourierServiceClient(RestTemplate restTemplate,
                                 @Value("${services.courier-service.base-url}") String courierServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.courierServiceBaseUrl = courierServiceBaseUrl;
    }

    public CourierReservationResult reserveCourier(Long courierId, Long orderId) {
        String url = courierServiceBaseUrl + "/courier/" + courierId + "/reserve/" + orderId;
        try {
            return restTemplate.exchange(url, HttpMethod.PUT, null, CourierReservationResult.class).getBody();
        } catch (HttpStatusCodeException ex) {
            log.warn("Courier reservation rejected for courierId={}, orderId={}: {}",
                    courierId, orderId, ex.getResponseBodyAsString());
            throw new CourierAssignmentException(
                    HttpStatus.valueOf(ex.getStatusCode().value()),
                    extractMessage(ex));
        } catch (ResourceAccessException ex) {
            log.error("courier-service unreachable while reserving courierId={}", courierId, ex);
            throw new CourierAssignmentException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Courier service is unavailable");
        }
    }

    private String extractMessage(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return ex.getStatusText();
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.has("message") ? node.get("message").asText() : body;
        } catch (Exception parseError) {
            return body;
        }
    }
}
