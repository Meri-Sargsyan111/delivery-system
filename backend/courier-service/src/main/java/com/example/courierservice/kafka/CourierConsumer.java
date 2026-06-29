package com.example.courierservice.kafka;

import com.example.courierservice.event.OrderCreatedEvent;
import com.example.courierservice.service.CourierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierConsumer {

    private final CourierService courierService;

    @KafkaListener(topics = "new-orders", groupId = "courier-group",
            containerFactory = "orderCreatedKafkaListenerContainerFactory")
    public void consume(OrderCreatedEvent event) {
        courierService.startDelivery(event.getOrderId());
        log.info("New order received and delivery started: orderId={}", event.getOrderId());
    }
}