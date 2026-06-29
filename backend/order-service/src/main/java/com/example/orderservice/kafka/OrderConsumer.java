package com.example.orderservice.kafka;

import com.example.orderservice.event.DeliveryUpdateEvent;
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
        if ("DELIVERED".equals(event.getStatus())) {
            orderService.deliverOrder(event.getOrderId());
            log.info("Order {} marked as DELIVERED via delivery update event", event.getOrderId());
        }
    }
}
