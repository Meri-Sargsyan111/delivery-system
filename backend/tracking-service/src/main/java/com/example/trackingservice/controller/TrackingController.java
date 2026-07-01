package com.example.trackingservice.controller;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @GetMapping("/{orderId}")
    public List<TrackingEvent> getTracking(@PathVariable Long orderId) {
        log.info("GET /tracking/{}", orderId);
        return trackingService.getTracking(orderId);
    }

    @GetMapping
    public Page<TrackingEvent> getAllEvents(@PageableDefault(size = 20) Pageable pageable) {
        return trackingService.getAllEvents(pageable);
    }
}