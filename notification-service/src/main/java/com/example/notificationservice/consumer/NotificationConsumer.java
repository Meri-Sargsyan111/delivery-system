package com.example.notificationservice;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationStore notificationStore;

    @KafkaListener(topics = "new-orders", groupId = "notification-group")
    public void listenNewOrders(String message) {
        String[] parts = message.split(":");

        System.out.println("📦 A new order has been received!");
        System.out.println("   Order ID: " + parts[0]);
        System.out.println("   Customer: " + parts[1]);
        System.out.println("   Address: " + parts[2]);

        String notification =
                "New Order -> ID: " + parts[0]
                        + ", Customer: " + parts[1]
                        + ", Address: " + parts[2];

        notificationStore.add(notification);
    }

    @KafkaListener(topics = "delivery-updates", groupId = "notification-group")
    public void listenDeliveryUpdates(String message) {
        String[] parts = message.split(":");

        System.out.println("🚗 Delivery status has changed!");
        System.out.println("   Order ID: " + parts[0]);
        System.out.println("   Courier: " + parts[1]);
        System.out.println("   Status: " + parts[2]);

        String notification =
                "Delivery Update -> Order: " + parts[0]
                        + ", Courier: " + parts[1]
                        + ", Status: " + parts[2];

        notificationStore.add(notification);
    }
}