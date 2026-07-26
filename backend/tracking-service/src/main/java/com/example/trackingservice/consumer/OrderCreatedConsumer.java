package com.example.trackingservice.consumer;

import com.example.trackingservice.entity.OrderOwnership;
import com.example.trackingservice.event.OrderCreatedEvent;
import com.example.trackingservice.repository.OrderOwnershipRepository;
import com.example.trackingservice.service.TrackingStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Builds the local OrderOwnership authorization projection from order-service's own
 * creation event - no synchronous call to order-service is needed. See OrderOwnership.
 * Also seeds the live-tracking TrackingState row (see TrackingStateService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final OrderOwnershipRepository orderOwnershipRepository;
    private final TrackingStateService trackingStateService;

    @KafkaListener(topics = "new-orders", groupId = "tracking-group",
            containerFactory = "orderCreatedKafkaListenerContainerFactory")
    public void listen(OrderCreatedEvent event) {
        OrderOwnership ownership = orderOwnershipRepository.findById(event.getOrderId())
                .orElseGet(() -> new OrderOwnership(event.getOrderId(), null, null));
        ownership.setCustomerUserId(event.getCustomerUserId());
        orderOwnershipRepository.save(ownership);

        log.info("Recorded order ownership: orderId={}, customerUserId={}",
                event.getOrderId(), event.getCustomerUserId());

        trackingStateService.onOrderCreated(event.getOrderId(), event.getFromAddress(), event.getToAddress());
    }
}