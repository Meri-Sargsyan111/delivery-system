package com.example.trackingservice.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * distance in meters, duration in seconds - see RoutingClient for the conversion.
 * Unlike ai-service's copy of this DTO, this one also carries geometry (requested via
 * overview=full&geometries=geojson) - tracking-service needs the actual route line to
 * draw/walk, not just distance/duration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsrmRoute(double distance, double duration, OsrmGeometry geometry) {
}
