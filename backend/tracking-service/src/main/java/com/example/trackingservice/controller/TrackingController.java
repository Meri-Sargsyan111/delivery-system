package com.example.trackingservice.controller;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
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