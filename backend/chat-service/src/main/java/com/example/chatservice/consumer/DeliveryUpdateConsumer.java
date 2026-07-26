package com.example.chatservice.consumer;

import com.example.chatservice.entity.OrderParticipants;
import com.example.chatservice.event.DeliveryUpdateEvent;
import com.example.chatservice.repository.OrderParticipantsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Learns the assigned courierUserId and the order's current status from the same
 * delivery-updates topic order-service/courier-service already publish to. orderStatus is
 * what ChatServiceImpl checks to disable sending once an order reaches a terminal state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryUpdateConsumer {

    private final OrderParticipantsRepository orderParticipantsRepository;

    @KafkaListener(topics = "delivery-updates", groupId = "chat-group",
            containerFactory = "deliveryUpdateKafkaListenerContainerFactory")
    public void listen(DeliveryUpdateEvent event) {
        OrderParticipants participants = orderParticipantsRepository.findById(event.getOrderId())
                .orElseGet(() -> new OrderParticipants(event.getOrderId(), null, null, null));

        if (event.getCourierUserId() != null) {
            participants.setCourierUserId(event.getCourierUserId());
            participants.setCourierName(event.getCourierName());
        }
        participants.setOrderStatus(event.getStatus());
        orderParticipantsRepository.save(participants);

        log.info("Updated chat participant: orderId={}, courierUserId={}, status={}",
                event.getOrderId(), event.getCourierUserId(), event.getStatus());
    }
}