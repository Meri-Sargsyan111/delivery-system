package com.example.trackingservice.routing;

import com.example.trackingservice.util.GeoMath;

import java.util.List;

/**
 * Matches a live courier position onto a precomputed route polyline. Forward-only,
 * windowed nearest-point search - starting from the last matched index and scanning at
 * most SEARCH_WINDOW vertices ahead - rather than a global nearest-point search over the
 * whole polyline: a global search can match a point on a route with a loop, a U-turn, or
 * two roughly-parallel segments to the wrong place, making route progress jump backward
 * or forward discontinuously. The matched index is clamped to never move backward
 * tick-to-tick even within the window, so routeProgressPercent is always monotonic.
 */
public final class RouteProjector {

    private static final int SEARCH_WINDOW = 50;

    private RouteProjector() {
    }

    public record ProjectionResult(
            int matchedIndex,
            double remainingDistanceKm,
            double routeProgressPercent,
            boolean deviated
    ) {}

    /**
     * @param polyline route points, [lat, lng] per entry, in route order
     * @param cumulativeDistanceKm same length as polyline, cumulative distance from the
     *                             first point to point i - see #cumulativeDistanceKm
     * @param lastMatchedIndex the previous tick's matchedIndex (0 on the first tick)
     * @param deviationThresholdMeters beyond this distance from the matched point, deviated=true
     */
    public static ProjectionResult project(List<double[]> polyline, double[] cumulativeDistanceKm,
                                            int lastMatchedIndex, double lat, double lng,
                                            double deviationThresholdMeters) {
        int start = Math.max(0, Math.min(lastMatchedIndex, polyline.size() - 1));
        int end = Math.min(polyline.size() - 1, start + SEARCH_WINDOW);

        int bestIndex = start;
        double bestDistanceMeters = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            double[] point = polyline.get(i);
            double distanceMeters = GeoMath.haversineMeters(lat, lng, point[0], point[1]);
            if (distanceMeters < bestDistanceMeters) {
                bestDistanceMeters = distanceMeters;
                bestIndex = i;
            }
        }
        bestIndex = Math.max(bestIndex, lastMatchedIndex);

        double totalKm = cumulativeDistanceKm[cumulativeDistanceKm.length - 1];
        double remainingKm = Math.max(0, totalKm - cumulativeDistanceKm[bestIndex]);
        double progressPercent = totalKm > 0 ? Math.min(100, (cumulativeDistanceKm[bestIndex] / totalKm) * 100) : 100;
        boolean deviated = bestDistanceMeters > deviationThresholdMeters;

        return new ProjectionResult(bestIndex, remainingKm, progressPercent, deviated);
    }

    /** Cumulative distance (km) from the first polyline point to each point, same length as polyline. */
    public static double[] cumulativeDistanceKm(List<double[]> polyline) {
        double[] cumulative = new double[polyline.size()];
        for (int i = 1; i < polyline.size(); i++) {
            double[] previous = polyline.get(i - 1);
            double[] current = polyline.get(i);
            cumulative[i] = cumulative[i - 1] + GeoMath.haversineKm(previous[0], previous[1], current[0], current[1]);
        }
        return cumulative;
    }
}
