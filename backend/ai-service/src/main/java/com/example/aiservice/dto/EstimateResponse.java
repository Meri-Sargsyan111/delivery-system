package com.example.aiservice.dto;

import java.util.UUID;

/**
 * Public API contract for POST /ai/estimate. estimatedPrice/estimatedDeliveryTime/
 * recommendedVehicle are all calculated deterministically (see PricingCalculator/
 * EtaFormatter/VehicleRecommender in service.impl) - only explanation comes from the
 * LLM, and only after everything else has already been decided.
 *
 * estimateId identifies the frozen quote persisted alongside this response (see
 * entity.Estimate) - payment-service claims it via POST /ai/estimate/{estimateId}/claim
 * to learn the authoritative amount to charge, rather than trusting a client-supplied
 * price or recomputing pricing itself.
 */
public record EstimateResponse(
        UUID estimateId,
        Double estimatedPrice,
        String estimatedDeliveryTime,
        RecommendedVehicle recommendedVehicle,
        String explanation,
        String currency

) {
}
