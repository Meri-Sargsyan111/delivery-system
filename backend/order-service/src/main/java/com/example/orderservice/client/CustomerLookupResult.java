package com.example.orderservice.client;

import java.util.UUID;

/**
 * Projection of auth-service's customer summary (see AuthServiceClient) - only the
 * fields order-service actually needs to populate a DeliveryOrder.
 */
public record CustomerLookupResult(UUID id, String firstName, String lastName) {
}