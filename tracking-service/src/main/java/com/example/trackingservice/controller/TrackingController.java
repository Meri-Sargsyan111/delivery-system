package com.example.trackingservice;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingEventRepository trackingEventRepository;

    @GetMapping("/{orderId}")
    public List<TrackingEvent> getTracking(@PathVariable Long orderId) {
        return trackingEventRepository.findByOrderId(orderId);
    }

    @GetMapping
    public List<TrackingEvent> getAllEvents() {
        return trackingEventRepository.findAll();
    }
}