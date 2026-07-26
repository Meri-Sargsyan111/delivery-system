package com.example.trackingservice.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** GeoJSON LineString - coordinates are [lon, lat] pairs, OSRM's order, not [lat, lon]. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsrmGeometry(List<List<Double>> coordinates) {
}
