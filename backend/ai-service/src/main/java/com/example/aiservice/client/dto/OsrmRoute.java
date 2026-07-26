package com.example.aiservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** distance in meters, duration in seconds - see RoutingClient for the conversion. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsrmRoute(double distance, double duration) {
}