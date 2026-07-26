package com.example.trackingservice.service;

import com.example.trackingservice.dto.AdminStatsResponse;
import com.example.trackingservice.dto.RouteResponse;
import com.example.trackingservice.dto.TrackingStateResponse;
import com.example.trackingservice.event.CourierLocationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Owns the live-tracking domain (TrackingState/DeliveryPhase) - position, ETA, remaining
 * distance, route progress - layered on top of the existing TrackingEvent status
 * timeline (still maintained separately, unchanged, by TrackingConsumer/TrackingService).
 */
public interface TrackingStateService {

    /** Called from OrderCreatedConsumer - seeds a WAITING_FOR_COURIER row, best-effort geocoded. */
    void onOrderCreated(Long orderId, String fromAddress, String toAddress);

    /** Called from TrackingConsumer alongside its existing TrackingEvent insert. */
    void onDeliveryStatusChanged(Long orderId, String status, Long courierId, UUID courierUserId);

    /** Called from LiveLocationConsumer on every courier-location-updates message. */
    void onLocationUpdate(CourierLocationEvent event);

    /** Access-checked per TrackingAccessGuard - same rule as GET /tracking/{orderId}. */
    TrackingStateResponse getLive(Long orderId);

    /** Access-checked per TrackingAccessGuard - fetches/caches the route if not already available. */
    RouteResponse getRoute(Long orderId);

    /** ADMIN only. */
    Page<TrackingStateResponse> getActiveDeliveries(Pageable pageable);

    /** ADMIN only. */
    AdminStatsResponse getStats();
}
