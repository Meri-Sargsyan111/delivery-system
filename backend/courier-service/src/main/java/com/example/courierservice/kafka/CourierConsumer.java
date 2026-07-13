package com.example.courierservice.kafka;

import com.example.courierservice.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Orders no longer auto-start delivery on creation: a dispatcher must explicitly
 * assign a courier first (CREATED -> ASSIGNED), then explicitly start delivery
 * (ASSIGNED -> IN_PROGRESS). This listener is kept for observability only.
 */
@Slf4j
@Service
public class CourierConsumer {

    @KafkaListener(topics = "new-orders", groupId = "courier-group",
            containerFactory = "orderCreatedKafkaListenerContainerFactory")
    public void consume(OrderCreatedEvent event) {
        log.info("New order {} created and awaiting dispatcher assignment", event.getOrderId());
    }
}