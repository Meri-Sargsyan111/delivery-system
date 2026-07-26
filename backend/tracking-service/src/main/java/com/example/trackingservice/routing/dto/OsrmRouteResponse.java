package com.example.trackingservice.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OsrmRouteResponse(List<OsrmRoute> routes) {
}
