package com.example.orderservice.service.impl;

import com.example.orderservice.order.RecommendedVehicle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decides which vehicle a courier should use for a new order, based on weight and
 * package description. A heavy-keyword match in the description always wins over the
 * weight thresholds, since e.g. a "5kg" listed weight for a piece of furniture is more
 * likely an error/underestimate than something a moped can actually carry.
 */
@Component
public class VehicleRecommender {

    private static final double MOPED_MAX_KG = 5;
    private static final double CAR_MAX_KG = 30;

    private static final List<String> HEAVY_KEYWORDS = List.of(
            "furniture", "refrigerator", "washing machine", "tv", "sofa", "bed", "cabinet"
    );

    public RecommendedVehicle recommend(Double weightKg, String packageDescription) {
        if (containsHeavyKeyword(packageDescription)) {
            return RecommendedVehicle.TRUCK;
        }
        if (weightKg == null) {
            return RecommendedVehicle.MOPED;
        }
        if (weightKg <= MOPED_MAX_KG) {
            return RecommendedVehicle.MOPED;
        }
        if (weightKg <= CAR_MAX_KG) {
            return RecommendedVehicle.CAR;
        }
        return RecommendedVehicle.TRUCK;
    }

    private boolean containsHeavyKeyword(String packageDescription) {
        if (packageDescription == null || packageDescription.isBlank()) {
            return false;
        }
        String lowerCaseDescription = packageDescription.toLowerCase();
        return HEAVY_KEYWORDS.stream().anyMatch(lowerCaseDescription::contains);
    }
}