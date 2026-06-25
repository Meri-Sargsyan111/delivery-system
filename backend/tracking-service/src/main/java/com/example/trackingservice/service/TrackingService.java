package com.example.trackingservice.service;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final TrackingEventRepository trackingEventRepository;

    public List<TrackingEvent> getTracking(Long orderId) {
        return trackingEventRepository.findByOrderId(orderId);
    }

    public List<TrackingEvent> getAllEvents() {
        return trackingEventRepository.findAll();
    }
}
