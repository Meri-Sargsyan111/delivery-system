package com.example.trackingservice.dto;

import java.time.LocalDateTime;

/**
 * Full live-tracking snapshot for one order - returned by GET /tracking/{orderId}/live
 * and broadcast (as-is) over /topic/tracking/order/{orderId} on every meaningful
 * location update. See TrackingState for field semantics.
 */
public record TrackingStateResponse(
        Long orderId,
        Long courierId,
        Double pickupLat,
        Double pickupLng,
        Double destinationLat,
        Double destinationLng,
        Double currentLat,
        Double currentLng,
        double bearing,
        double speedKmh,
        Double remainingDistanceKm,
        Double remainingDurationMin,
        double routeProgressPercent,
        boolean deviated,
        String phase,
        String vehicleType,
        LocalDateTime eta,
        LocalDateTime lastUpdate
) {}
