package com.example.aiservice.service.impl;

import com.example.aiservice.dto.RecommendedVehicle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic vehicle selection - never left to the model (see EstimateServiceImpl:
 * earlier live testing showed Ollama does not reliably apply rules like these itself).
 * <p>
 * Priority order (first match wins):
 * 1. Heavy-cargo keywords, or weight over 20kg regardless of description -> TRUCK
 * 2. Furniture/bulky-appliance keywords -> VAN (even if lightweight - these are bulky,
 *    not just heavy, so weight alone doesn't capture why a moped can't carry them)
 * 3. Document keywords under 2kg, or just under 2kg generally -> BIKE
 * 4. Under 5kg -> MOPED
 * 5. Otherwise (5-20kg) -> CAR
 */
@Component
public class VehicleRecommender {

    private static final double BIKE_MAX_KG = 2;
    private static final double MOPED_MAX_KG = 5;
    private static final double CAR_MAX_KG = 20;

    private static final List<String> HEAVY_CARGO_KEYWORDS = List.of(
            "machinery", "heavy cargo", "industrial equipment", "construction material", "pallet"
    );

    private static final List<String> FURNITURE_KEYWORDS = List.of(
            "furniture", "sofa", "bed", "cabinet", "wardrobe", "refrigerator", "washing machine", "tv"
    );

    private static final List<String> DOCUMENT_KEYWORDS = List.of(
            "document", "documents", "envelope", "letter"
    );

    public RecommendedVehicle recommend(Double weightKg, String packageDescription) {
        String description = packageDescription == null ? "" : packageDescription.toLowerCase();
        double weight = weightKg == null ? 0 : weightKg;

        if (containsAny(description, HEAVY_CARGO_KEYWORDS) || weight > CAR_MAX_KG) {
            return RecommendedVehicle.TRUCK;
        }
        if (containsAny(description, FURNITURE_KEYWORDS)) {
            return RecommendedVehicle.VAN;
        }
        if (containsAny(description, DOCUMENT_KEYWORDS) && weight < BIKE_MAX_KG) {
            return RecommendedVehicle.BIKE;
        }
        if (weight < BIKE_MAX_KG) {
            return RecommendedVehicle.BIKE;
        }
        if (weight < MOPED_MAX_KG) {
            return RecommendedVehicle.MOPED;
        }
        return RecommendedVehicle.CAR;
    }

    private boolean containsAny(String description, List<String> keywords) {
        return keywords.stream().anyMatch(description::contains);
    }
}