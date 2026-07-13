package com.example.trackingservice.controller;

import com.example.trackingservice.dto.TrackingEventResponse;
import com.example.trackingservice.service.TrackingService;
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

    /**
     * ADMIN sees any order's tracking; CUSTOMER/COURIER access is scoped to their own
     * order/assignment inside TrackingServiceImpl (see requireAccess there).
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<List<TrackingEventResponse>> getTracking(@PathVariable Long orderId) {
        log.info("GET /tracking/{}", orderId);
        return ResponseEntity.ok(trackingService.getTracking(orderId));
    }

    /** ADMIN only - enforced in TrackingServiceImpl.getAllEvents(). */
    @GetMapping
    public ResponseEntity<Page<TrackingEventResponse>> getAllEvents(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(trackingService.getAllEvents(pageable));
    }
}