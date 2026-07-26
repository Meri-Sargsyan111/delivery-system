package com.example.courierservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to courier-location-updates on every meaningful location change (see
 * LocationServiceImpl's throttling) - consumed by tracking-service to maintain live
 * TrackingState (position, ETA, remaining distance, route progress) and rebroadcast an
 * enriched update over its own per-order WebSocket topic. Replaces the old direct
 * "/topic/location" broadcast, which had no per-order subscribe authorization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierLocationEvent {

    private Long orderId;
    private Long courierId;
    private UUID courierUserId;
    private double latitude;
    private double longitude;
    private double bearing;
    private double speedKmh;
    private Instant timestamp;

    /** Name of courier-service's VehicleType enum value, e.g. "CAR" - null on legacy/unset couriers. */
    private String vehicleType;
}
