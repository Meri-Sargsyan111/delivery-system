package com.example.courierservice.dto;

import java.util.UUID;

/**
 * Response of the internal PUT /courier/{courierId}/reserve/{orderId} call, consumed
 * only by order-service's CourierServiceClient. Carries the courier's linked user id
 * so order-service can store a locally-checkable ownership link on the order.
 */
public record ReserveCourierResponse(Long courierId, UUID courierUserId) {
}