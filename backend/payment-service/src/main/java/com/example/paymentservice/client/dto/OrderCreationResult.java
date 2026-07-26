package com.example.paymentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Local view of order-service's OrderResponse - only the id matters here. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreationResult(Long id, String message) {
}
