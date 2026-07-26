package com.example.courierservice.service.impl;

import com.example.courierservice.client.TrackingServiceClient;
import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.dto.RouteView;
import com.example.courierservice.service.LocationService;
import com.example.courierservice.service.LocationSimulatorService;
import com.example.courierservice.util.GeoMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Simulates courier movement along the REAL driving route fetched from tracking-service
 * (see TrackingServiceClient), one simulation per active order, rather than a single
 * hardcoded 6-point loop shared globally. Position at any tick is derived from elapsed
 * time and a configurable speed, interpolated along the route's cumulative distance -
 * not a per-tick index step - so simulated speed is independent of how densely OSRM's
 * polyline happens to sample a given road.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationSimulatorServiceImpl implements LocationSimulatorService {

    private static final long SIMULATION_INTERVAL_MS = 3000;

    /** Safety valve: a missed stopTracking (e.g. a lost CANCELLED event) must not loop forever. */
    private static final long MAX_SIMULATION_AGE_MS = 2 * 60 * 60 * 1000;

    private final LocationService locationService;
    private final TrackingServiceClient trackingServiceClient;

    @Value("${courier.simulation.speed-kmh:40}")
    private double speedKmh;

    public void setSpeedKmh(double speedKmh) {
        this.speedKmh = speedKmh;
    }

    private final Map<Long, SimulationState> simulations = new ConcurrentHashMap<>();

    /** Overridable in tests so elapsed-time-based movement is deterministic, not wall-clock-flaky. */
    private LongSupplier clockMillis = System::currentTimeMillis;

    public void setClockMillis(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    private record SimulationState(
            List<double[]> polyline,
            double[] cumulativeDistanceKm,
            double totalDistanceKm,
            long startedAtMs
    ) {}

    @Override
    public void startTracking(Long orderId, Long courierId, UUID courierUserId) {
        RouteView route = trackingServiceClient.getRoute(orderId);
        if (route == null || route.geometry() == null || route.geometry().size() < 2) {
            log.warn("Could not start live tracking for orderId={}: no route available from tracking-service", orderId);
            return;
        }

        List<double[]> polyline = route.geometry().stream()
                .map(point -> new double[]{point.get(0), point.get(1)})
                .toList();

        double[] cumulative = new double[polyline.size()];
        for (int i = 1; i < polyline.size(); i++) {
            double[] previous = polyline.get(i - 1);
            double[] current = polyline.get(i);
            cumulative[i] = cumulative[i - 1] + GeoMath.haversineKm(previous[0], previous[1], current[0], current[1]);
        }
        double totalDistanceKm = cumulative[cumulative.length - 1];

        simulations.put(orderId, new SimulationState(polyline, cumulative, totalDistanceKm, clockMillis.getAsLong()));
        log.info("Live tracking started for orderId={}, courierId={}, routeDistanceKm={}",
                orderId, courierId, totalDistanceKm);
    }

    @Override
    public void stopTracking(Long orderId) {
        if (simulations.remove(orderId) != null) {
            log.info("Live tracking stopped for orderId={}", orderId);
        }
    }

    @Override
    @Scheduled(fixedDelay = SIMULATION_INTERVAL_MS)
    public void simulateMovement() {
        long now = clockMillis.getAsLong();

        for (Map.Entry<Long, SimulationState> entry : simulations.entrySet()) {
            Long orderId = entry.getKey();
            SimulationState state = entry.getValue();

            if (now - state.startedAtMs() > MAX_SIMULATION_AGE_MS) {
                simulations.remove(orderId, state);
                log.warn("Evicted orphaned simulation for orderId={} after exceeding max simulation age", orderId);
                continue;
            }

            advance(orderId, state, now);
        }
    }

    private void advance(Long orderId, SimulationState state, long now) {
        double elapsedHours = (now - state.startedAtMs()) / 3_600_000.0;
        double targetKm = Math.min(speedKmh * elapsedHours, state.totalDistanceKm());

        int segment = findSegment(state.cumulativeDistanceKm(), targetKm);
        double[] from = state.polyline().get(segment);
        double[] to = state.polyline().get(Math.min(segment + 1, state.polyline().size() - 1));

        double segmentStartKm = state.cumulativeDistanceKm()[segment];
        double segmentLengthKm = state.cumulativeDistanceKm()[Math.min(segment + 1, state.cumulativeDistanceKm().length - 1)] - segmentStartKm;
        double fraction = segmentLengthKm > 0 ? (targetKm - segmentStartKm) / segmentLengthKm : 0;
        fraction = Math.max(0, Math.min(1, fraction));

        double lat = from[0] + (to[0] - from[0]) * fraction;
        double lng = from[1] + (to[1] - from[1]) * fraction;
        double bearing = GeoMath.initialBearing(from[0], from[1], to[0], to[1]);

        CourierLocation location = new CourierLocation(orderId, lat, lng, bearing, speedKmh);
        log.debug("Simulating courier at [{}, {}] for orderId={} ({} / {} km)",
                lat, lng, orderId, String.format("%.2f", targetKm), String.format("%.2f", state.totalDistanceKm()));
        locationService.sendLocation(location);
    }

    /** Forward-only-by-nature since targetKm only increases over time - a simple scan is fine here. */
    private int findSegment(double[] cumulativeDistanceKm, double targetKm) {
        for (int i = 0; i < cumulativeDistanceKm.length - 1; i++) {
            if (targetKm <= cumulativeDistanceKm[i + 1]) {
                return i;
            }
        }
        return cumulativeDistanceKm.length - 2 < 0 ? 0 : cumulativeDistanceKm.length - 2;
    }
}
