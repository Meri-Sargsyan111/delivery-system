package com.example.orderservice.service.impl;

import com.example.orderservice.client.AuthServiceClient;
import com.example.orderservice.client.CustomerLookupResult;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Decides which customer a new order belongs to.
 * <p>
 * CUSTOMER: always the authenticated caller - customerId in the request body is never
 * read for this role, so a forged/borrowed id can never be used to impersonate another
 * customer.
 * <p>
 * ADMIN: the customerId they picked (required for this role), resolved - and validated
 * to exist - via auth-service.
 */
@Component
@RequiredArgsConstructor
public class OrderCustomerResolver {

    private final AuthServiceClient authServiceClient;
    private final CurrentUser currentUser;

    public CustomerLookupResult resolve(CreateOrderRequest request) {
        return authServiceClient.getCustomerById(resolveCustomerId(request));
    }

    private UUID resolveCustomerId(CreateOrderRequest request) {
        if (currentUser.isAdmin()) {
            return requireCustomerId(request);
        }
        return currentUser.getUserId();
    }

    private UUID requireCustomerId(CreateOrderRequest request) {
        if (request.getCustomerId() == null) {
            throw new IllegalArgumentException("customerId is required when creating an order as ADMIN");
        }
        return request.getCustomerId();
    }
}