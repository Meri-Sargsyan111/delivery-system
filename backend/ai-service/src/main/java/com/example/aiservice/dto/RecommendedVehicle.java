package com.example.aiservice.dto;

/**
 * ai-service's own vehicle set for the estimation feature - deliberately more granular
 * than order-service's own RecommendedVehicle enum (which only has MOPED/CAR/TRUCK).
 * ai-service is a separate deployable with no shared module, so the two are independent
 * by design; see VehicleRecommender/PricingCalculator for how each value is used.
 */
public enum RecommendedVehicle {
    BIKE,
    MOPED,
    CAR,
    VAN,
    TRUCK
}