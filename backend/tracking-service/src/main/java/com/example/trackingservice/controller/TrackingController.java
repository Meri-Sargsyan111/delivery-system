package com.example.trackingservice.controller;

import com.example.trackingservice.dto.TrackingEventResponse;
import com.example.trackingservice.mapper.TrackingEventMapper;
import com.example.trackingservice.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final TrackingEventMapper trackingEventMapper;

    @GetMapping("/{orderId}")
    public ResponseEntity<List<TrackingEventResponse>> getTracking(@PathVariable Long orderId) {
        log.info("GET /tracking/{}", orderId);
        return ResponseEntity.ok(trackingEventMapper.toResponseList(trackingService.getTracking(orderId)));
    }

    @GetMapping
    public ResponseEntity<Page<TrackingEventResponse>> getAllEvents(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(trackingService.getAllEvents(pageable).map(trackingEventMapper::toResponse));
    }
}