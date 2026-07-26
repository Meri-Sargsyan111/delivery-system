package com.example.aiservice.service.impl;

import com.example.aiservice.dto.RecommendedVehicle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleRecommenderTest {

    private final VehicleRecommender recommender = new VehicleRecommender();

    @Test
    void documentsUnderTwoKg_recommendsBike() {
        assertThat(recommender.recommend(1.0, "Documents")).isEqualTo(RecommendedVehicle.BIKE);
    }

    @Test
    void anyPackageUnderTwoKg_recommendsBike() {
        assertThat(recommender.recommend(1.5, "Small gift")).isEqualTo(RecommendedVehicle.BIKE);
    }

    @Test
    void smallPackageUnderFiveKg_recommendsMoped() {
        assertThat(recommender.recommend(3.0, "Small package")).isEqualTo(RecommendedVehicle.MOPED);
    }

    @Test
    void mediumPackageUnderTwentyKg_recommendsCar() {
        assertThat(recommender.recommend(15.0, "Medium package")).isEqualTo(RecommendedVehicle.CAR);
    }

    @Test
    void furnitureKeyword_recommendsVanRegardlessOfLightWeight() {
        assertThat(recommender.recommend(3.0, "Furniture")).isEqualTo(RecommendedVehicle.VAN);
    }

    @Test
    void bulkyApplianceKeywords_recommendVan() {
        assertThat(recommender.recommend(3.0, "Refrigerator")).isEqualTo(RecommendedVehicle.VAN);
        assertThat(recommender.recommend(3.0, "Washing Machine")).isEqualTo(RecommendedVehicle.VAN);
        assertThat(recommender.recommend(3.0, "TV")).isEqualTo(RecommendedVehicle.VAN);
    }

    @Test
    void heavyCargoKeyword_recommendsTruckRegardlessOfWeight() {
        assertThat(recommender.recommend(1.0, "Heavy cargo")).isEqualTo(RecommendedVehicle.TRUCK);
    }

    @Test
    void weightOverTwentyKg_recommendsTruckEvenWithoutKeyword() {
        assertThat(recommender.recommend(45.0, "Boxes")).isEqualTo(RecommendedVehicle.TRUCK);
    }

    @Test
    void weightExactlyTwentyKg_staysCar() {
        assertThat(recommender.recommend(20.0, "Boxes")).isEqualTo(RecommendedVehicle.CAR);
    }

    @Test
    void nullWeightAndDescription_doesNotThrowAndDefaultsToBike() {
        assertThat(recommender.recommend(null, null)).isEqualTo(RecommendedVehicle.BIKE);
    }

    @Test
    void keywordMatchingIsCaseInsensitive() {
        assertThat(recommender.recommend(3.0, "FURNITURE set")).isEqualTo(RecommendedVehicle.VAN);
    }
}