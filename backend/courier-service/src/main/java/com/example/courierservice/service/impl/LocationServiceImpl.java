package com.example.courierservice.service.impl;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.entity.Courier;
import com.example.courierservice.entity.CourierAssignment;
import com.example.courierservice.event.CourierLocationEvent;
import com.example.courierservice.repository.CourierAssignmentRepository;
import com.example.courierservice.repository.CourierRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.LocationService;
import com.example.courierservice.util.GeoMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes courier location updates to Kafka (courier-location-updates) for
 * tracking-service to consume, enrich with ETA/route-progress, and rebroadcast on its
 * own per-order, authorization-checked WebSocket topic. Previously broadcast directly to
 * a global "/topic/location" WebSocket topic with no per-order subscribe authorization -
 * that let any subscriber see every courier's live position regardless of order
 * ownership, a real privacy gap. Retired in favor of the Kafka + tracking-service path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final KafkaTemplate<String, CourierLocationEvent> kafkaTemplate;
    private final CourierAssignmentRepository courierAssignmentRepository;
    private final CourierRepository courierRepository;
    private final CurrentUser currentUser;

    @Value("${courier.location.min-interval-ms:5000}")
    private long minIntervalMs;

    @Value("${courier.location.min-distance-meters:15}")
    private double minDistanceMeters;

    private final Map<Long, LastSent> lastSentByOrder = new ConcurrentHashMap<>();

    private record LastSent(double lat, double lng, long sentAtMs) {}

    @Override
    public void sendLocation(CourierLocation location) {
        requireAdminOrAssignedCourier(location.getOrderId());

        CourierAssignment assignment = courierAssignmentRepository.findByOrderId(location.getOrderId()).orElse(null);
        if (assignment == null) {
            log.warn("No assignment found for orderId={}, dropping location update", location.getOrderId());
            return;
        }
        Courier courier = courierRepository.findById(assignment.getCourierId()).orElse(null);
        if (courier == null) {
            log.warn("Assignment for orderId={} references missing courier {}",
                    location.getOrderId(), assignment.getCourierId());
            return;
        }

        if (!isMeaningfulChange(location)) {
            return;
        }

        String vehicleType = courier.getVehicleType() != null ? courier.getVehicleType().name() : null;
        CourierLocationEvent event = new CourierLocationEvent(
                location.getOrderId(), courier.getId(), courier.getUserId(),
                location.getLatitude(), location.getLongitude(),
                location.getBearing(), location.getSpeedKmh(), Instant.now(), vehicleType);

        log.debug("Publishing location update for orderId={}: lat={}, lon={}",
                location.getOrderId(), location.getLatitude(), location.getLongitude());

        try {
            kafkaTemplate.send("courier-location-updates", event);
        } catch (Exception e) {
            log.error("Failed to publish location update for orderId={}", location.getOrderId(), e);
        }
    }

    /**
     * Only publishes when the courier has moved far enough, or enough time has passed,
     * to be worth a DB write + broadcast downstream in tracking-service - avoids
     * hammering Kafka/Postgres with near-duplicate positions every 3s tick.
     */
    private boolean isMeaningfulChange(CourierLocation location) {
        LastSent previous = lastSentByOrder.get(location.getOrderId());
        long now = System.currentTimeMillis();

        if (previous != null) {
            double movedMeters = GeoMath.haversineMeters(
                    previous.lat(), previous.lng(), location.getLatitude(), location.getLongitude());
            boolean enoughTimePassed = now - previous.sentAtMs() >= minIntervalMs;
            boolean movedFarEnough = movedMeters >= minDistanceMeters;
            if (!enoughTimePassed && !movedFarEnough) {
                return false;
            }
        }

        lastSentByOrder.put(location.getOrderId(), new LastSent(location.getLatitude(), location.getLongitude(), now));
        return true;
    }

    /**
     * Only an ADMIN or the courier actually assigned to this order may push a location
     * update for it - never trust the orderId alone as proof of assignment.
     */
    private void requireAdminOrAssignedCourier(Long orderId) {
        if (!currentUser.isAuthenticated() || currentUser.isAdmin()) {
            return;
        }
        if (isAssignedCourierForOrder(orderId)) {
            return;
        }
        throw new AccessDeniedException("Not authorized to update location for order " + orderId);
    }

    private boolean isAssignedCourierForOrder(Long orderId) {
        CourierAssignment assignment = courierAssignmentRepository.findByOrderId(orderId).orElse(null);
        if (assignment == null || !currentUser.isCourier()) {
            return false;
        }
        return courierRepository.findByUserId(currentUser.getUserId())
                .map(courier -> courier.getId().equals(assignment.getCourierId()))
                .orElse(false);
    }
}
