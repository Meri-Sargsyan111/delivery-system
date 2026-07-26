package com.example.aiservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Projection of one entry in Nominatim's /search JSON array response - lat/lon come
 * back as strings, not numbers, hence GeocodingClient parses them itself.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NominatimResult(String lat, String lon) {
}