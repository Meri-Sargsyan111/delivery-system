package com.example.orderservice.kafka;

import com.example.orderservice.event.DeliveryUpdateEvent;
import com.example.orderservice.exception.InvalidOrderStateException;
import com.example.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = "delivery-updates", groupId = "order-group",
            containerFactory = "deliveryUpdateKafkaListenerContainerFactory")
    public void consume(DeliveryUpdateEvent event) {
        try {
            switch (event.getStatus()) {
                case "IN_PROGRESS" -> {
                    orderService.startProgress(event.getOrderId());
                    log.info("Order {} marked as IN_PROGRESS via delivery update event", event.getOrderId());
                }
                case "DELIVERED" -> {
                    orderService.deliverOrder(event.getOrderId());
                    log.info("Order {} marked as DELIVERED via delivery update event", event.getOrderId());
                }
                default -> log.debug("Ignoring delivery update with status {} for order {}",
                        event.getStatus(), event.getOrderId());
            }
        } catch (InvalidOrderStateException ex) {
            log.warn("Skipped delivery update for order {} (status {}): {}",
                    event.getOrderId(), event.getStatus(), ex.getMessage());
        }
    }
}
