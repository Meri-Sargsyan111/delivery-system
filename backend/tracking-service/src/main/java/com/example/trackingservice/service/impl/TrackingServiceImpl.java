package com.example.trackingservice.service.impl;

import com.example.trackingservice.dto.TrackingEventResponse;
import com.example.trackingservice.entity.OrderOwnership;
import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.mapper.TrackingEventMapper;
import com.example.trackingservice.repository.OrderOwnershipRepository;
import com.example.trackingservice.repository.TrackingEventRepository;
import com.example.trackingservice.security.CurrentUser;
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
    private final OrderOwnershipRepository orderOwnershipRepository;
    private final CurrentUser currentUser;
    private final TrackingEventMapper trackingEventMapper;

    @Override
    public List<TrackingEventResponse> getTracking(Long orderId) {
        requireAccess(orderId);

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

    /**
     * ADMIN sees any order's tracking; CUSTOMER only their own order; COURIER only an
     * order assigned to them, per the local OrderOwnership projection built from Kafka
     * events (see OrderOwnership). An order with no recorded ownership yet (event not
     * consumed, or predates ownership tracking) is safely admin-only rather than
     * guessed-accessible to whoever asks for that orderId.
     */
    private void requireAccess(Long orderId) {
        if (currentUser.isAdmin()) {
            return;
        }

        OrderOwnership ownership = orderOwnershipRepository.findById(orderId).orElse(null);
        if (ownership != null) {
            if (currentUser.isCustomer() && currentUser.getUserId().equals(ownership.getCustomerUserId())) {
                return;
            }
            if (currentUser.isCourier() && currentUser.getUserId().equals(ownership.getCourierUserId())) {
                return;
            }
        }

        throw new AccessDeniedException("Not authorized to access tracking for order " + orderId);
    }
}