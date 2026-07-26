package com.example.trackingservice.service.impl;

import com.example.trackingservice.dto.AdminStatsResponse;
import com.example.trackingservice.dto.RouteResponse;
import com.example.trackingservice.dto.TrackingStateResponse;
import com.example.trackingservice.entity.DeliveryPhase;
import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.entity.TrackingState;
import com.example.trackingservice.event.CourierLocationEvent;
import com.example.trackingservice.event.TrackingLifecycleEvent;
import com.example.trackingservice.exception.EntityNotFoundException;
import com.example.trackingservice.repository.TrackingEventRepository;
import com.example.trackingservice.repository.TrackingStateRepository;
import com.example.trackingservice.routing.GeocodingClient;
import com.example.trackingservice.routing.RouteProjector;
import com.example.trackingservice.routing.RoutingClient;
import com.example.trackingservice.routing.RoutingUnavailableException;
import com.example.trackingservice.routing.dto.Coordinate;
import com.example.trackingservice.routing.dto.RouteResult;
import com.example.trackingservice.security.CurrentUser;
import com.example.trackingservice.security.TrackingAccessGuard;
import com.example.trackingservice.service.TrackingStateService;
import com.example.trackingservice.util.GeoMath;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingStateServiceImpl implements TrackingStateService {

    private static final Set<DeliveryPhase> TERMINAL_PHASES = EnumSet.of(DeliveryPhase.DELIVERED, DeliveryPhase.CANCELLED);

    private static final Map<DeliveryPhase, DeliveryPhase> DWELL_TIMEOUT_NEXT_PHASE = Map.of(
            DeliveryPhase.COURIER_EN_ROUTE_PICKUP, DeliveryPhase.PICKED_UP,
            DeliveryPhase.PICKED_UP, DeliveryPhase.IN_TRANSIT,
            DeliveryPhase.IN_TRANSIT, DeliveryPhase.ARRIVING
    );

    private static final double DEFAULT_FALLBACK_SPEED_KMH = 30.0;
    private static final double DEFAULT_LEG_DURATION_MIN = 60.0;

    private final TrackingStateRepository trackingStateRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final TrackingAccessGuard trackingAccessGuard;
    private final CurrentUser currentUser;
    private final GeocodingClient geocodingClient;
    private final RoutingClient routingClient;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, TrackingLifecycleEvent> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${tracking.pickup-radius-meters:150}")
    private double pickupRadiusMeters;

    @Value("${tracking.destination-radius-meters:300}")
    private double destinationRadiusMeters;

    @Value("${tracking.route-deviation-threshold-meters:200}")
    private double routeDeviationThresholdMeters;

    @Override
    @Transactional
    public void onOrderCreated(Long orderId, String fromAddress, String toAddress) {
        if (trackingStateRepository.existsById(orderId)) {
            return;
        }

        TrackingState state = new TrackingState();
        state.setOrderId(orderId);
        state.setFromAddress(fromAddress);
        state.setToAddress(toAddress);
        state.setPhase(DeliveryPhase.WAITING_FOR_COURIER);
        state.setPhaseChangedAt(LocalDateTime.now());
        state.setLastUpdate(LocalDateTime.now());

        Coordinate pickup = safeGeocode(fromAddress);
        if (pickup != null) {
            state.setPickupLat(pickup.lat());
            state.setPickupLng(pickup.lon());
        }
        Coordinate destination = safeGeocode(toAddress);
        if (destination != null) {
            state.setDestinationLat(destination.lat());
            state.setDestinationLng(destination.lon());
        }

        trackingStateRepository.save(state);
        log.info("TrackingState created for orderId={}, phase=WAITING_FOR_COURIER, geocoded={}",
                orderId, pickup != null && destination != null);
    }

    @Override
    @Transactional
    public void onDeliveryStatusChanged(Long orderId, String status, Long courierId, UUID courierUserId) {
        DeliveryPhase newPhase = mapStatusToPhase(status);
        if (newPhase == null) {
            return;
        }

        TrackingState state = trackingStateRepository.findById(orderId).orElseGet(() -> newState(orderId));

        if (courierId != null) {
            state.setCourierId(courierId);
        }
        if (courierUserId != null) {
            state.setCourierUserId(courierUserId);
        }

        if (state.getPhase() == newPhase || TERMINAL_PHASES.contains(state.getPhase())) {
            trackingStateRepository.save(state);
            return;
        }

        applyPhaseChange(state, newPhase, false);
        trackingStateRepository.save(state);
        broadcast(state);
    }

    @Override
    @Transactional
    public void onLocationUpdate(CourierLocationEvent event) {
        Long orderId = event.getOrderId();
        TrackingState state = trackingStateRepository.findById(orderId).orElse(null);
        if (state == null) {
            log.warn("Received location update for unknown orderId={}, dropping", orderId);
            return;
        }
        if (TERMINAL_PHASES.contains(state.getPhase())) {
            return;
        }

        state.setCurrentLat(event.getLatitude());
        state.setCurrentLng(event.getLongitude());
        state.setBearing(event.getBearing());
        state.setSpeedKmh(event.getSpeedKmh());
        if (event.getVehicleType() != null) {
            state.setVehicleType(event.getVehicleType());
        }
        if (event.getCourierId() != null) {
            state.setCourierId(event.getCourierId());
        }
        if (event.getCourierUserId() != null) {
            state.setCourierUserId(event.getCourierUserId());
        }
        state.setLastUpdate(LocalDateTime.now());

        ensureRoute(state);
        recalculateEta(state, event);
        applyDwellTimeoutIfStuck(state);

        trackingStateRepository.save(state);
        broadcast(state);
    }

    private void recalculateEta(TrackingState state, CourierLocationEvent event) {
        List<double[]> polyline = parseGeometry(state.getRouteGeometryJson());
        if (polyline == null || polyline.isEmpty()) {
            return;
        }

        double[] cumulative = RouteProjector.cumulativeDistanceKm(polyline);
        RouteProjector.ProjectionResult projection = RouteProjector.project(
                polyline, cumulative, state.getLastMatchedSegmentIndex(),
                event.getLatitude(), event.getLongitude(), routeDeviationThresholdMeters);

        state.setLastMatchedSegmentIndex(projection.matchedIndex());
        state.setRouteProgressPercent(projection.routeProgressPercent());
        state.setDeviated(projection.deviated());

        double remainingKm;
        double remainingMin;
        if (projection.deviated() && state.getDestinationLat() != null) {
            remainingKm = GeoMath.haversineKm(event.getLatitude(), event.getLongitude(),
                    state.getDestinationLat(), state.getDestinationLng());
            double speed = event.getSpeedKmh() > 0 ? event.getSpeedKmh() : DEFAULT_FALLBACK_SPEED_KMH;
            remainingMin = (remainingKm / speed) * 60.0;
        } else {
            remainingKm = projection.remainingDistanceKm();
            double totalKm = state.getRouteDistanceKm() != null ? state.getRouteDistanceKm() : 0;
            double totalMin = state.getRouteDurationMin() != null ? state.getRouteDurationMin() : 0;
            remainingMin = totalKm > 0 ? totalMin * (remainingKm / totalKm) : 0;
        }
        state.setRemainingDistanceKm(remainingKm);
        state.setRemainingDurationMin(remainingMin);
        state.setEta(LocalDateTime.now().plusSeconds(Math.round(remainingMin * 60)));

        inferPhaseFromProximity(state, event);
    }

    private void inferPhaseFromProximity(TrackingState state, CourierLocationEvent event) {
        DeliveryPhase phase = state.getPhase();
        if (phase == DeliveryPhase.COURIER_EN_ROUTE_PICKUP && state.getPickupLat() != null) {
            double distanceToPickupM = GeoMath.haversineMeters(
                    event.getLatitude(), event.getLongitude(), state.getPickupLat(), state.getPickupLng());
            if (distanceToPickupM <= pickupRadiusMeters) {
                applyPhaseChange(state, DeliveryPhase.PICKED_UP, true);
            }
        } else if (phase == DeliveryPhase.PICKED_UP) {
            applyPhaseChange(state, DeliveryPhase.IN_TRANSIT, true);
        } else if (phase == DeliveryPhase.IN_TRANSIT && state.getDestinationLat() != null) {
            double distanceToDestM = GeoMath.haversineMeters(
                    event.getLatitude(), event.getLongitude(), state.getDestinationLat(), state.getDestinationLng());
            if (distanceToDestM <= destinationRadiusMeters) {
                applyPhaseChange(state, DeliveryPhase.ARRIVING, true);
            }
        }
    }

    /**
     * Safety valve for a phase that never got its expected proximity/status signal (real
     * GPS drift, a lost event) - bounds how long an order can look "stuck" by auto-
     * advancing at 2x the route's expected leg duration. Never advances past ARRIVING;
     * DELIVERED/CANCELLED remain authoritative-only (see onDeliveryStatusChanged).
     */
    private void applyDwellTimeoutIfStuck(TrackingState state) {
        DeliveryPhase next = DWELL_TIMEOUT_NEXT_PHASE.get(state.getPhase());
        if (next == null || state.getPhaseChangedAt() == null) {
            return;
        }
        double legDurationMin = state.getRouteDurationMin() != null ? state.getRouteDurationMin() : DEFAULT_LEG_DURATION_MIN;
        long dwellMinutes = Duration.between(state.getPhaseChangedAt(), LocalDateTime.now()).toMinutes();
        if (dwellMinutes > legDurationMin * 2) {
            log.warn("Order {} stuck in phase {} for {}min (>2x expected leg duration {}min), auto-advancing to {}",
                    state.getOrderId(), state.getPhase(), dwellMinutes, legDurationMin, next);
            applyPhaseChange(state, next, true);
        }
    }

    @Override
    public TrackingStateResponse getLive(Long orderId) {
        trackingAccessGuard.requireAccess(orderId);
        TrackingState state = trackingStateRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("No tracking state found for order " + orderId));
        return toResponse(state);
    }

    @Override
    @Transactional
    public RouteResponse getRoute(Long orderId) {
        trackingAccessGuard.requireAccess(orderId);
        TrackingState state = trackingStateRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("No tracking state found for order " + orderId));

        ensureRoute(state);
        trackingStateRepository.save(state);

        if (state.getRouteGeometryJson() == null) {
            throw new RoutingUnavailableException("Route is not yet available for order " + orderId);
        }

        List<double[]> geometry = parseGeometry(state.getRouteGeometryJson());
        List<List<Double>> geometryOut = geometry.stream().map(p -> List.of(p[0], p[1])).toList();

        return new RouteResponse(geometryOut, state.getRouteDistanceKm(), state.getRouteDurationMin(),
                state.getPickupLat(), state.getPickupLng(), state.getDestinationLat(), state.getDestinationLng());
    }

    @Override
    public Page<TrackingStateResponse> getActiveDeliveries(Pageable pageable) {
        requireAdmin();
        return trackingStateRepository.findByPhaseNotIn(TERMINAL_PHASES, pageable).map(this::toResponse);
    }

    @Override
    public AdminStatsResponse getStats() {
        requireAdmin();
        long activeDeliveries = trackingStateRepository.countByPhaseNotIn(TERMINAL_PHASES);
        long activeCouriers = trackingStateRepository.countByCourierUserIdIsNotNullAndPhaseNotIn(TERMINAL_PHASES);
        long completedToday = trackingStateRepository.countByPhaseAndPhaseChangedAtAfter(
                DeliveryPhase.DELIVERED, LocalDate.now().atStartOfDay());
        return new AdminStatsResponse(activeDeliveries, activeCouriers, completedToday);
    }

    private void ensureRoute(TrackingState state) {
        if (state.getRouteGeometryJson() != null) {
            return;
        }
        if (state.getPickupLat() == null && state.getFromAddress() != null) {
            Coordinate pickup = safeGeocode(state.getFromAddress());
            if (pickup != null) {
                state.setPickupLat(pickup.lat());
                state.setPickupLng(pickup.lon());
            }
        }
        if (state.getDestinationLat() == null && state.getToAddress() != null) {
            Coordinate destination = safeGeocode(state.getToAddress());
            if (destination != null) {
                state.setDestinationLat(destination.lat());
                state.setDestinationLng(destination.lon());
            }
        }
        if (state.getPickupLat() == null || state.getDestinationLat() == null) {
            return;
        }

        try {
            RouteResult route = routingClient.route(
                    new Coordinate(state.getPickupLat(), state.getPickupLng()),
                    new Coordinate(state.getDestinationLat(), state.getDestinationLng()));
            if (route == null) {
                return;
            }
            state.setRouteDistanceKm(route.distanceKm());
            state.setRouteDurationMin(route.durationMinutes());
            state.setRouteGeometryJson(objectMapper.writeValueAsString(route.geometry()));
        } catch (Exception e) {
            log.warn("Failed to compute route for orderId={}, will retry on next update", state.getOrderId(), e);
        }
    }

    private void applyPhaseChange(TrackingState state, DeliveryPhase newPhase, boolean insertTimelineEvent) {
        state.setPhase(newPhase);
        state.setPhaseChangedAt(LocalDateTime.now());
        state.setLastUpdate(LocalDateTime.now());

        if (insertTimelineEvent) {
            TrackingEvent event = new TrackingEvent();
            event.setOrderId(state.getOrderId());
            event.setStatus(newPhase.name());
            event.setEventTime(LocalDateTime.now());
            trackingEventRepository.save(event);
        }

        publishLifecycleEvent(state.getOrderId(), newPhase);
        log.info("Order {} phase changed to {}", state.getOrderId(), newPhase);
    }

    private void publishLifecycleEvent(Long orderId, DeliveryPhase phase) {
        try {
            kafkaTemplate.send("tracking-events", new TrackingLifecycleEvent(orderId, phase.name(), LocalDateTime.now()));
        } catch (Exception e) {
            log.error("Failed to publish tracking lifecycle event for order {} phase {}", orderId, phase, e);
        }
    }

    private void broadcast(TrackingState state) {
        TrackingStateResponse response = toResponse(state);
        messagingTemplate.convertAndSend("/topic/tracking/order/" + state.getOrderId(), response);
        messagingTemplate.convertAndSend("/topic/tracking/admin/live", response);
    }

    private TrackingStateResponse toResponse(TrackingState state) {
        return new TrackingStateResponse(
                state.getOrderId(), state.getCourierId(),
                state.getPickupLat(), state.getPickupLng(),
                state.getDestinationLat(), state.getDestinationLng(),
                state.getCurrentLat(), state.getCurrentLng(),
                state.getBearing(), state.getSpeedKmh(),
                state.getRemainingDistanceKm(), state.getRemainingDurationMin(),
                state.getRouteProgressPercent(), state.isDeviated(),
                state.getPhase() != null ? state.getPhase().name() : null,
                state.getVehicleType(),
                state.getEta(), state.getLastUpdate());
    }

    private List<double[]> parseGeometry(String json) {
        if (json == null) {
            return null;
        }
        try {
            return List.of(objectMapper.readValue(json, double[][].class));
        } catch (Exception e) {
            log.warn("Failed to parse cached route geometry", e);
            return null;
        }
    }

    private Coordinate safeGeocode(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        try {
            return geocodingClient.geocode(address);
        } catch (Exception e) {
            log.warn("Geocoding failed for '{}', will retry lazily later", address, e);
            return null;
        }
    }

    private DeliveryPhase mapStatusToPhase(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "ASSIGNED" -> DeliveryPhase.COURIER_ACCEPTED;
            case "IN_PROGRESS" -> DeliveryPhase.COURIER_EN_ROUTE_PICKUP;
            case "DELIVERED" -> DeliveryPhase.DELIVERED;
            case "CANCELLED" -> DeliveryPhase.CANCELLED;
            default -> null;
        };
    }

    private TrackingState newState(Long orderId) {
        TrackingState state = new TrackingState();
        state.setOrderId(orderId);
        state.setPhase(DeliveryPhase.WAITING_FOR_COURIER);
        state.setPhaseChangedAt(LocalDateTime.now());
        state.setLastUpdate(LocalDateTime.now());
        return state;
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only ADMIN may access this endpoint");
        }
    }
}
