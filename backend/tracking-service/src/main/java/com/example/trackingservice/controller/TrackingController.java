package com.example.trackingservice.controller;

import com.example.trackingservice.dto.AdminStatsResponse;
import com.example.trackingservice.dto.RouteResponse;
import com.example.trackingservice.dto.TrackingEventResponse;
import com.example.trackingservice.dto.TrackingStateResponse;
import com.example.trackingservice.service.TrackingService;
import com.example.trackingservice.service.TrackingStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final TrackingStateService trackingStateService;

    /**
     * ADMIN sees any order's tracking; CUSTOMER/COURIER access is scoped to their own
     * order/assignment inside TrackingServiceImpl (see requireAccess there). Unchanged
     * status-timeline endpoint - see GET /{orderId}/live for the new live-tracking view.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<List<TrackingEventResponse>> getTracking(@PathVariable Long orderId) {
        log.info("GET /tracking/{}", orderId);
        return ResponseEntity.ok(trackingService.getTracking(orderId));
    }

    /** ADMIN only - enforced in TrackingServiceImpl.getAllEvents(). Unchanged. */
    @GetMapping
    public ResponseEntity<Page<TrackingEventResponse>> getAllEvents(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(trackingService.getAllEvents(pageable));
    }

    /** Current position/ETA/remaining distance/phase - same access rule as GET /{orderId}. */
    @GetMapping("/{orderId}/live")
    public ResponseEntity<TrackingStateResponse> getLive(@PathVariable Long orderId) {
        log.info("GET /tracking/{}/live", orderId);
        return ResponseEntity.ok(trackingStateService.getLive(orderId));
    }

    /**
     * Route geometry/distance/duration - same access rule as GET /{orderId}. Used
     * internally by courier-service's simulator (see TrackingServiceClient there) and
     * exposable to a future frontend map view.
     */
    @GetMapping("/{orderId}/route")
    public ResponseEntity<RouteResponse> getRoute(@PathVariable Long orderId) {
        log.info("GET /tracking/{}/route", orderId);
        return ResponseEntity.ok(trackingStateService.getRoute(orderId));
    }

    /** ADMIN only - active (non-terminal) deliveries with live position/ETA. */
    @GetMapping("/admin/active")
    public ResponseEntity<Page<TrackingStateResponse>> getActiveDeliveries(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(trackingStateService.getActiveDeliveries(pageable));
    }

    /** ADMIN only - active delivery/courier counts and today's completed count. */
    @GetMapping("/admin/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(trackingStateService.getStats());
    }
}