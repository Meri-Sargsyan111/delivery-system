package com.example.trackingservice.entity;

/**
 * Fine-grained, GPS-aware delivery lifecycle owned entirely by tracking-service -
 * deliberately separate from order-service's own OrderStatus (CREATED/ASSIGNED/
 * IN_PROGRESS/DELIVERED/CANCELLED), which stays exactly as-is as the coarse business-
 * lifecycle gate order-service already validates transitions against. Renaming/expanding
 * OrderStatus itself would change DeliveryOrderResponse.status's existing serialized
 * values - a real API contract change this design deliberately avoids.
 *
 * ORDER_CREATED from the original spec is folded into WAITING_FOR_COURIER: order
 * creation and "no courier yet" are simultaneous in this system, there's no separate
 * searching-for-courier step.
 */
public enum DeliveryPhase {
    WAITING_FOR_COURIER,
    COURIER_ACCEPTED,
    COURIER_EN_ROUTE_PICKUP,
    PICKED_UP,
    IN_TRANSIT,
    ARRIVING,
    DELIVERED,
    CANCELLED
}
