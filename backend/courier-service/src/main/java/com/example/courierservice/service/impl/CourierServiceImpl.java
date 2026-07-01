package com.example.courierservice.service.impl;

import com.example.courierservice.entity.CourierUpdate;
import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.repository.CourierUpdateRepository;
import com.example.courierservice.service.CourierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierServiceImpl implements CourierService {

    private final CourierUpdateRepository courierUpdateRepository;
    private final KafkaTemplate<String, DeliveryUpdateEvent> kafkaTemplate;

    @Override
    public String startDelivery(Long orderId) {

        CourierUpdate update = new CourierUpdate();

        update.setOrderId(orderId);
        update.setCourierName("System");
        update.setStatus("IN_PROGRESS");
        courierUpdateRepository.save(update);

        kafkaTemplate.send("delivery-updates", new DeliveryUpdateEvent(orderId, "System", "IN_PROGRESS"));
        log.info("Delivery started for orderId: {}, event published to Kafka", orderId);

        return "Delivery started";
    }

    @Override
    public String markAsDelivered(Long orderId) {

        CourierUpdate update = new CourierUpdate();

        update.setOrderId(orderId);
        update.setCourierName("System");
        update.setStatus("DELIVERED");
        courierUpdateRepository.save(update);

        kafkaTemplate.send("delivery-updates", new DeliveryUpdateEvent(orderId, "System", "DELIVERED"));
        log.info("Order {} marked as DELIVERED, event published to Kafka", orderId);

        return "Delivery completed";
    }
}
