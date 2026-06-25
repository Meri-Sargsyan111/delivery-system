package com.example.courierservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CourierConsumer {

    @KafkaListener(topics = "new-orders", groupId = "courier-group")
    public void consume(String message) {
        log.info("New Order Received: {}", message);
    }
}