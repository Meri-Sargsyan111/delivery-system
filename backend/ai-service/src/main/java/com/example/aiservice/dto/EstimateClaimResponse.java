package com.example.aiservice.dto;

import java.util.UUID;

/**
 * Response for POST /ai/estimate/{estimateId}/claim - the frozen quote plus every
 * delivery detail order-service needs to build the order, so payment-service never has
 * to ask the client for these again after the estimate was originally confirmed.
 */
public record EstimateClaimResponse(
        UUID estimateId,
        String fromAddress,
        String toAddress,
        String packageDescription,
        Double weightKg,
        Double amount,
        String currency,
        String estimatedDeliveryTime,
        RecommendedVehicle recommendedVehicle
) {
}
