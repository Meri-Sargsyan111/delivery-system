package com.example.trackingservice.event;

import java.time.LocalDateTime;

/**
 * Published to tracking-events on every DeliveryPhase change (WAITING_FOR_COURIER ->
 * COURIER_ACCEPTED -> COURIER_EN_ROUTE_PICKUP -> PICKED_UP -> IN_TRANSIT -> ARRIVING ->
 * DELIVERED, or CANCELLED at any point) - carries the phase itself rather than a
 * separately-maintained set of event-name synonyms, so downstream consumers have the
 * full lifecycle vocabulary rather than a lossy re-encoding of it.
 */
public record TrackingLifecycleEvent(Long orderId, String phase, LocalDateTime timestamp) {
}
