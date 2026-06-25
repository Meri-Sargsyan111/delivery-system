package com.example.trackingservice.controller;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class TrackingController {

    private final TrackingService trackingService;

    @GetMapping("/{orderId}")
    public List<TrackingEvent> getTracking(@PathVariable Long orderId) {
        return trackingService.getTracking(orderId);
    }

    @GetMapping
    public List<TrackingEvent> getAllEvents() {
        return trackingService.getAllEvents();
    }
}