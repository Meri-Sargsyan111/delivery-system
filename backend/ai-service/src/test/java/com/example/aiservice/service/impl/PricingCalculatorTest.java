package com.example.aiservice.service.impl;

import com.example.aiservice.dto.RecommendedVehicle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @Test
    void baseCase_zeroDistanceLightBike_isJustTheBasePrice() {
        assertThat(calculator.calculate(0, 1.0, RecommendedVehicle.BIKE)).isEqualTo(500.0);
    }

    @Test
    void distanceIsAddedAtRatePerKm() {
        assertThat(calculator.calculate(10, 1.0, RecommendedVehicle.BIKE)).isEqualTo(1700.0);
    }

    @Test
    void weightSurcharge_appliesCorrectBracket() {
        assertThat(calculator.calculate(0, 2.0, RecommendedVehicle.BIKE)).isEqualTo(500.0);
        assertThat(calculator.calculate(0, 5.0, RecommendedVehicle.BIKE)).isEqualTo(700.0);
        assertThat(calculator.calculate(0, 10.0, RecommendedVehicle.BIKE)).isEqualTo(1000.0);
        assertThat(calculator.calculate(0, 20.0, RecommendedVehicle.BIKE)).isEqualTo(1500.0);
        assertThat(calculator.calculate(0, 25.0, RecommendedVehicle.BIKE)).isEqualTo(2500.0);
    }

    @Test
    void vehicleMultiplier_appliesToWholeSubtotal() {
        assertThat(calculator.calculate(0, 1.0, RecommendedVehicle.BIKE)).isEqualTo(500.0);
        assertThat(calculator.calculate(0, 1.0, RecommendedVehicle.MOPED)).isEqualTo(550.0);
        assertThat(calculator.calculate(0, 1.0, RecommendedVehicle.CAR)).isEqualTo(650.0);
        assertThat(calculator.calculate(0, 1.0, RecommendedVehicle.VAN)).isEqualTo(850.0);
        assertThat(calculator.calculate(0, 1.0, RecommendedVehicle.TRUCK)).isEqualTo(1250.0);
    }

    @Test
    void nullWeight_treatedAsZeroSurcharge() {
        assertThat(calculator.calculate(0, null, RecommendedVehicle.BIKE)).isEqualTo(500.0);
    }

    @Test
    void realisticExample_distanceWeightAndVehicleCombined() {
        assertThat(calculator.calculate(12, 3.0, RecommendedVehicle.CAR)).isEqualTo(2782.0);
    }
}
