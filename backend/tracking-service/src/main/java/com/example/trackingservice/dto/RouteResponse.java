package com.example.trackingservice.dto;

import java.util.List;

/**
 * Route geometry/distance/duration for one order's pickup->destination pair. Consumed
 * both by courier-service's simulator (see courier-service's RouteView, which ignores
 * the extra pickup/destination fields via @JsonIgnoreProperties) and, eventually, a
 * frontend map view. geometry entries are [lat, lng] pairs in route order.
 */
public record RouteResponse(
        List<List<Double>> geometry,
        double distanceKm,
        double durationMinutes,
        Double pickupLat,
        Double pickupLng,
        Double destinationLat,
        Double destinationLng
) {}
