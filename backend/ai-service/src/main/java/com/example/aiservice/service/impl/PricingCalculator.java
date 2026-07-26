package com.example.aiservice.service.impl;

import com.example.aiservice.dto.RecommendedVehicle;
import org.springframework.stereotype.Component;

/**
 * Deterministic price calculation - replaces having the LLM invent a price. Formula:
 * (base + distance*rate + weight surcharge) * vehicle multiplier.
 */
@Component
public class PricingCalculator {

    private static final double BASE_PRICE_AMD = 500;
    private static final double PRICE_PER_KM_AMD = 120;

    public double calculate(double distanceKm, Double weightKg, RecommendedVehicle vehicle) {
        double subtotal = BASE_PRICE_AMD + (distanceKm * PRICE_PER_KM_AMD) + weightSurcharge(weightKg);
        return Math.round(subtotal * vehicleMultiplier(vehicle));
    }

    private double weightSurcharge(Double weightKg) {
        double weight = weightKg == null ? 0 : weightKg;
        if (weight <= 2) {
            return 0;
        }
        if (weight <= 5) {
            return 200;
        }
        if (weight <= 10) {
            return 500;
        }
        if (weight <= 20) {
            return 1000;
        }
        return 2000;
    }

    private double vehicleMultiplier(RecommendedVehicle vehicle) {
        return switch (vehicle) {
            case BIKE -> 1.0;
            case MOPED -> 1.1;
            case CAR -> 1.3;
            case VAN -> 1.7;
            case TRUCK -> 2.5;
        };
    }
}