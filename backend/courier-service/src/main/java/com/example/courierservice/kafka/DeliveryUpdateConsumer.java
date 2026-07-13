package com.example.courierservice.kafka;

import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.service.CourierAssignmentService;
import com.example.courierservice.service.LocationSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Reacts to order-side cancellations published on {@code delivery-updates} so the
 * courier assigned to a cancelled order is freed back to AVAILABLE. Order-service
 * owns the cancellation decision; courier-service only owns courier state, so this
 * propagates asynchronously rather than via a synchronous call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryUpdateConsumer {

    private final CourierAssignmentService courierAssignmentService;
    private final LocationSimulatorService locationSimulatorService;

    @KafkaListener(topics = "delivery-updates", groupId = "courier-group",
            containerFactory = "deliveryUpdateKafkaListenerContainerFactory")
    public void consume(DeliveryUpdateEvent event) {
        if ("CANCELLED".equals(event.getStatus())) {
            courierAssignmentService.releaseCourierForOrder(event.getOrderId());
            locationSimulatorService.stopTracking(event.getOrderId());
            log.info("Order {} cancelled, released its assigned courier", event.getOrderId());
        }
    }
}
