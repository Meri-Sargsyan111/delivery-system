package com.example.aiservice.service.impl;

import com.example.aiservice.dto.RecommendedVehicle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EtaFormatterTest {

    private final EtaFormatter formatter = new EtaFormatter();

    @Test
    void underAnHour_formatsAsMinutesOnly() {
        assertThat(formatter.format(25, RecommendedVehicle.CAR)).isEqualTo("35 minutes");
    }

    @Test
    void singularMinute_isNotPluralized() {
        assertThat(formatter.format(0, RecommendedVehicle.BIKE)).isEqualTo("5 minutes");
    }

    @Test
    void exactlyOneHour_omitsMinutesPart() {
        assertThat(formatter.format(55, RecommendedVehicle.BIKE)).isEqualTo("1 hour");
    }

    @Test
    void hoursAndMinutes_bothIncluded() {
        assertThat(formatter.format(120, RecommendedVehicle.VAN)).isEqualTo("2 hours 15 minutes");
    }

    @Test
    void singularHourWithMinutes() {
        assertThat(formatter.format(45, RecommendedVehicle.TRUCK)).isEqualTo("1 hour 5 minutes");
    }

    @Test
    void neverProducesVagueRangeLikeDays() {
        String result = formatter.format(2000, RecommendedVehicle.TRUCK);
        assertThat(result).doesNotContain("day");
        assertThat(result).matches("\\d+ hours? ?(\\d+ minutes?)?");
    }
}