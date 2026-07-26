package com.example.courierservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Local view of tracking-service's GET /tracking/{orderId}/route response - just enough
 * for the simulator to walk a real polyline instead of a hardcoded 6-point loop. Each
 * entry in geometry is a [lat, lng] pair, in route order from pickup to destination.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteView(
        List<List<Double>> geometry,
        double distanceKm,
        double durationMinutes
) {}
