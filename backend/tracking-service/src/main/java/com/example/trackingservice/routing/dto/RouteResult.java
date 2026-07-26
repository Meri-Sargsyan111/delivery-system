package com.example.trackingservice.routing.dto;

import java.util.List;

/** geometry is in [lat, lng] order per point, already converted from OSRM's [lon, lat]. */
public record RouteResult(double distanceKm, double durationMinutes, List<double[]> geometry) {
}
