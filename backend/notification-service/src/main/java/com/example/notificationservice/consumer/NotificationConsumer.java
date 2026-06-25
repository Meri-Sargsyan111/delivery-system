package com.example.notificationservice.consumer;

import com.example.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationStore;

    @KafkaListener(topics = "new-orders", groupId = "notification-group")
    public void listenNewOrders(String message) {

        log.info("Received new order message: {}", message);

        String[] parts = message.split(":");

        String notification =
                "New Order -> ID: " + parts[0]
                        + ", Customer: " + parts[1]
                        + ", Address: " + parts[2];

        notificationStore.add(notification);
    }

    @KafkaListener(topics = "delivery-updates", groupId = "notification-group")
    public void listenDeliveryUpdates(String message) {

        log.info("Received delivery update: {}", message);

        String[] parts = message.split(":");

        String notification =
                "Delivery Update -> Order: " + parts[0]
                        + ", Courier: " + parts[1]
                        + ", Status: " + parts[2];

        notificationStore.add(notification);
    }
}