package com.example.chatservice.consumer;

import com.example.chatservice.entity.OrderParticipants;
import com.example.chatservice.event.OrderCreatedEvent;
import com.example.chatservice.repository.OrderParticipantsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Builds the local OrderParticipants authorization projection from order-service's own
 * creation event - no synchronous call to order-service is needed. Mirrors
 * tracking-service's OrderCreatedConsumer exactly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final OrderParticipantsRepository orderParticipantsRepository;

    @KafkaListener(topics = "new-orders", groupId = "chat-group",
            containerFactory = "orderCreatedKafkaListenerContainerFactory")
    public void listen(OrderCreatedEvent event) {
        OrderParticipants participants = orderParticipantsRepository.findById(event.getOrderId())
                .orElseGet(() -> new OrderParticipants(event.getOrderId(), null, null, "CREATED"));
        participants.setCustomerUserId(event.getCustomerUserId());
        participants.setCustomerName(event.getCustomerName());
        orderParticipantsRepository.save(participants);

        log.info("Recorded chat participant: orderId={}, customerUserId={}",
                event.getOrderId(), event.getCustomerUserId());
    }
}