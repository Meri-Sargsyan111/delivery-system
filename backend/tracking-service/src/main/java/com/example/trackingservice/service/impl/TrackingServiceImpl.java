package com.example.trackingservice.service.impl;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.repository.TrackingEventRepository;
import com.example.trackingservice.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingServiceImpl implements TrackingService {

    private final TrackingEventRepository trackingEventRepository;

    @Override
    public List<TrackingEvent> getTracking(Long orderId) {
        List<TrackingEvent> events = trackingEventRepository.findByOrderId(orderId);
        if (events.isEmpty()) {
            log.warn("No tracking events found for orderId: {}", orderId);
        } else {
            log.info("Found {} tracking event(s) for orderId: {}", events.size(), orderId);
        }
        return events;
    }

    @Override
    public Page<TrackingEvent> getAllEvents(Pageable pageable) {
        return trackingEventRepository.findAll(pageable);
    }
}
