package com.example.courierservice.service.impl;

import com.example.courierservice.client.OrderServiceClient;
import com.example.courierservice.dto.RemoteOrderView;
import com.example.courierservice.entity.CourierUpdate;
import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.exception.InvalidOrderStateException;
import com.example.courierservice.repository.CourierUpdateRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.CourierAssignmentService;
import com.example.courierservice.service.CourierService;
import com.example.courierservice.service.LocationSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierServiceImpl implements CourierService {

    private static final Set<String> DELIVERABLE_STATUSES = Set.of("ASSIGNED", "IN_PROGRESS");

    private final CourierUpdateRepository courierUpdateRepository;
    private final KafkaTemplate<String, DeliveryUpdateEvent> kafkaTemplate;
    private final OrderServiceClient orderServiceClient;
    private final CourierAssignmentService courierAssignmentService;
    private final LocationSimulatorService locationSimulatorService;
    private final CurrentUser currentUser;

    @Override
    public String startDelivery(Long orderId) {

        RemoteOrderView order = orderServiceClient.getOrder(orderId);
        requireAdminOrAssignedCourier(order);

        if (!"ASSIGNED".equals(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " cannot start delivery: current status is " + order.getStatus());
        }

        recordAndPublishStatusUpdate(orderId, order, "IN_PROGRESS");
        log.info("Delivery started for orderId: {}, event published to Kafka", orderId);

        locationSimulatorService.startTracking(orderId);

        return "Delivery started";
    }

    @Override
    public String markAsDelivered(Long orderId) {

        RemoteOrderView order = orderServiceClient.getOrder(orderId);
        requireAdminOrAssignedCourier(order);

        if (!DELIVERABLE_STATUSES.contains(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " cannot be delivered: current status is " + order.getStatus());
        }

        recordAndPublishStatusUpdate(orderId, order, "DELIVERED");
        log.info("Order {} marked as DELIVERED, event published to Kafka", orderId);

        courierAssignmentService.releaseCourierForOrder(orderId);
        locationSimulatorService.stopTracking(orderId);

        return "Delivery completed";
    }

    private void recordAndPublishStatusUpdate(Long orderId, RemoteOrderView order, String status) {
        String courierName = courierAssignmentService.getAssignment(orderId).getCourierName();

        CourierUpdate update = new CourierUpdate();
        update.setOrderId(orderId);
        update.setCourierName(courierName);
        update.setStatus(status);
        courierUpdateRepository.save(update);

        kafkaTemplate.send("delivery-updates",
                new DeliveryUpdateEvent(orderId, courierName, status, order.getCourierUserId()));
    }

    /**
     * ADMIN or the courier this order is actually assigned to (order.courierUserId,
     * populated by order-service from courier-service's own reserve-courier response) -
     * never trust the orderId path variable alone as proof of assignment.
     */
    private void requireAdminOrAssignedCourier(RemoteOrderView order) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (currentUser.isCourier() && currentUser.getUserId().equals(order.getCourierUserId())) {
            return;
        }
        throw new AccessDeniedException("Not authorized to act on order " + order.getId());
    }
}