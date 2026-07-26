package com.example.aiservice.service.impl;

import com.example.aiservice.dto.RecommendedVehicle;
import org.springframework.stereotype.Component;

/**
 * Formats a real driving duration (from the routing API) plus a per-vehicle loading
 * time into a natural phrase like "35 minutes" or "2 hours 15 minutes" - never an
 * invented range like "1-2 days" for a route the routing API actually measured.
 */
@Component
public class EtaFormatter {

    public String format(double drivingDurationMinutes, RecommendedVehicle vehicle) {
        long totalMinutes = Math.round(drivingDurationMinutes + loadingTimeMinutes(vehicle));
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours == 0) {
            return pluralize(minutes, "minute");
        }
        String hoursPart = pluralize(hours, "hour");
        if (minutes == 0) {
            return hoursPart;
        }
        return hoursPart + " " + pluralize(minutes, "minute");
    }

    private double loadingTimeMinutes(RecommendedVehicle vehicle) {
        return switch (vehicle) {
            case BIKE, MOPED -> 5;
            case CAR -> 10;
            case VAN -> 15;
            case TRUCK -> 20;
        };
    }

    private String pluralize(long value, String unit) {
        return value + " " + unit + (value == 1 ? "" : "s");
    }
}