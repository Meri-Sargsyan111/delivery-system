package com.example.orderservice.client;

import com.example.orderservice.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Synchronous call to auth-service to resolve the customer an admin picked when
 * creating an order on their behalf. Order creation needs the customer's name right
 * away to populate the order, so this goes over REST rather than Kafka.
 */
@Slf4j
@Component
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private final String authServiceBaseUrl;

    public AuthServiceClient(RestTemplate restTemplate,
                              @Value("${services.auth-service.base-url}") String authServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.authServiceBaseUrl = authServiceBaseUrl;
    }

    public CustomerLookupResult getCustomerById(UUID customerId) {
        String url = authServiceBaseUrl + "/customers/" + customerId;
        try {
            return restTemplate.getForObject(url, CustomerLookupResult.class);
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Customer not found with id: {}", customerId);
            throw new EntityNotFoundException("Customer not found with id: " + customerId);
        }
    }
}