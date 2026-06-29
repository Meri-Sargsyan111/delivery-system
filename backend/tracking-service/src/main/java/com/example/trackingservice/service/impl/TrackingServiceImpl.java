package com.example.trackingservice.service.impl;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.repository.TrackingEventRepository;
import com.example.trackingservice.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackingServiceImpl implements TrackingService {

    private final TrackingEventRepository trackingEventRepository;

    @Override
    public List<TrackingEvent> getTracking(Long orderId) {
        return trackingEventRepository.findByOrderId(orderId);
    }

    @Override
    public Page<TrackingEvent> getAllEvents(Pageable pageable) {
        return trackingEventRepository.findAll(pageable);
    }
}
