package com.example.orderservice.client;

import java.util.UUID;

/**
 * Result of successfully reserving a courier via courier-service. Carries the
 * courier's linked auth-service user id (may be null for a legacy/unlinked
 * courier record) so order-service can store a locally-checkable ownership
 * link on the order without any further cross-service calls at read time.
 */
public record CourierReservationResult(Long courierId, UUID courierUserId) {
}