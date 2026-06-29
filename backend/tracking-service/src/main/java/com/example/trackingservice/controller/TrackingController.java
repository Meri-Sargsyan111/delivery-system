package com.example.trackingservice.controller;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    @GetMapping("/{orderId}")
    public List<TrackingEvent> getTracking(@PathVariable Long orderId) {
        return trackingService.getTracking(orderId);
    }

    @GetMapping
    public Page<TrackingEvent> getAllEvents(@PageableDefault(size = 20) Pageable pageable) {
        return trackingService.getAllEvents(pageable);
    }
}