package com.example.courierservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class CourierConsumer {

    @KafkaListener(topics = "new-orders", groupId = "courier-group")
    public void consume(String message) {
        System.out.println("New Order Received: " + message);
    }
}
