package com.example.notificationservice.consumer;

import com.example.notificationservice.entity.NotificationType;
import com.example.notificationservice.event.DeliveryUpdateEvent;
import com.example.notificationservice.event.OrderCreatedEvent;
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

    @KafkaListener(topics = "new-orders", groupId = "notification-group",
            containerFactory = "orderCreatedKafkaListenerContainerFactory")
    public void listenNewOrders(OrderCreatedEvent event) {

        log.info("Received new order: orderId={}", event.getOrderId());

        String notification = "New Order -> ID: " + event.getOrderId()
                + ", Customer: " + event.getCustomerName()
                + ", Address: " + event.getToAddress();

        notificationStore.add(notification, event.getCustomerUserId(), NotificationType.NEW_ORDER, false);
    }

    /**
     * ASSIGNED is the one status change that means "a courier just received a delivery" -
     * that's the concrete case a courier app would want to play an alert sound for, so it
     * gets its own type/playSound instead of the generic DELIVERY_UPDATE treatment.
     */
    @KafkaListener(topics = "delivery-updates", groupId = "notification-group",
            containerFactory = "deliveryUpdateKafkaListenerContainerFactory")
    public void listenDeliveryUpdates(DeliveryUpdateEvent event) {

        log.info("Received delivery update: orderId={}, status={}", event.getOrderId(), event.getStatus());

        String notification = "Delivery Update -> Order: " + event.getOrderId()
                + ", Courier: " + event.getCourierName()
                + ", Status: " + event.getStatus();

        boolean courierAssigned = "ASSIGNED".equals(event.getStatus());
        NotificationType type = courierAssigned ? NotificationType.COURIER_ASSIGNED : NotificationType.DELIVERY_UPDATE;

        notificationStore.add(notification, event.getCourierUserId(), type, courierAssigned);
    }
}
