package com.example.trackingservice.util;

/**
 * Small great-circle math helpers - not accurate for road-following distance (that's
 * OSRM's job, see routing/RoutingClient), just for the cheap local calculations needed
 * per location tick: route-matching distance and remaining-route arc length.
 */
public final class GeoMath {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoMath() {
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        return haversineKm(lat1, lon1, lat2, lon2) * 1000.0;
    }
}
