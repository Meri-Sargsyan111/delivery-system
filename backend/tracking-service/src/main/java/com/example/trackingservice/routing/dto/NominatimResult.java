package com.example.trackingservice.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Projection of one entry in Nominatim's /search JSON array response - lat/lon come
 * back as strings, not numbers, hence GeocodingClient parses them itself. Mirrors
 * ai-service's copy of the same DTO (no shared module in this codebase).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NominatimResult(String lat, String lon) {
}
