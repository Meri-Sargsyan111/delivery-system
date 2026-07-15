package com.example.courierservice.service.impl;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.entity.CourierAssignment;
import com.example.courierservice.repository.CourierAssignmentRepository;
import com.example.courierservice.repository.CourierRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final CourierAssignmentRepository courierAssignmentRepository;
    private final CourierRepository courierRepository;
    private final CurrentUser currentUser;

    @Override
    public void sendLocation(CourierLocation location) {
        requireAdminOrAssignedCourier(location.getOrderId());

        log.debug("Broadcasting location for orderId: {}, lat={}, lon={}",
                location.getOrderId(), location.getLatitude(), location.getLongitude());
        messagingTemplate.convertAndSend("/topic/location", location);
    }

    /**
     * Only an ADMIN or the courier actually assigned to this order may push a location
     * update for it - never trust the orderId alone as proof of assignment.
     */
    private void requireAdminOrAssignedCourier(Long orderId) {
        if (!currentUser.isAuthenticated() || currentUser.isAdmin()) {
            return;
        }
        if (isAssignedCourierForOrder(orderId)) {
            return;
        }
        throw new AccessDeniedException("Not authorized to update location for order " + orderId);
    }

    private boolean isAssignedCourierForOrder(Long orderId) {
        CourierAssignment assignment = courierAssignmentRepository.findByOrderId(orderId).orElse(null);
        if (assignment == null || !currentUser.isCourier()) {
            return false;
        }
        return courierRepository.findByUserId(currentUser.getUserId())
                .map(courier -> courier.getId().equals(assignment.getCourierId()))
                .orElse(false);
    }
}
