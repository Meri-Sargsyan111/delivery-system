package com.example.courierservice.service.impl;

import com.example.courierservice.entity.CourierUpdate;
import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.repository.CourierUpdateRepository;
import com.example.courierservice.service.CourierService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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

        return "Delivery completed";
    }
}
