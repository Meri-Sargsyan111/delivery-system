package com.example.trackingservice.service.impl;

import com.example.trackingservice.dto.TrackingEventResponse;
import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.mapper.TrackingEventMapper;
import com.example.trackingservice.repository.TrackingEventRepository;
import com.example.trackingservice.security.CurrentUser;
import com.example.trackingservice.security.TrackingAccessGuard;
import com.example.trackingservice.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingServiceImpl implements TrackingService {

    private final TrackingEventRepository trackingEventRepository;
    private final CurrentUser currentUser;
    private final TrackingEventMapper trackingEventMapper;
    private final TrackingAccessGuard trackingAccessGuard;

    @Override
    public List<TrackingEventResponse> getTracking(Long orderId) {
        trackingAccessGuard.requireAccess(orderId);

        List<TrackingEvent> events = trackingEventRepository.findByOrderId(orderId);
        if (events.isEmpty()) {
            log.warn("No tracking events found for orderId: {}", orderId);
        } else {
            log.info("Found {} tracking event(s) for orderId: {}", events.size(), orderId);
        }
        return trackingEventMapper.toResponseList(events);
    }

    @Override
    public Page<TrackingEventResponse> getAllEvents(Pageable pageable) {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only ADMIN may list tracking events across all orders");
        }
        return trackingEventRepository.findAll(pageable).map(trackingEventMapper::toResponse);
    }
}