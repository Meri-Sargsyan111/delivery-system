package com.example.courierservice.service.impl;

import com.example.courierservice.entity.CourierUpdate;
import com.example.courierservice.repository.CourierUpdateRepository;
import com.example.courierservice.service.DeliveryStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryStatusServiceImpl implements DeliveryStatusService {

    private final CourierUpdateRepository courierUpdateRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void completeDelivery(Long orderId) {

        CourierUpdate update = new CourierUpdate();

        update.setOrderId(orderId);
        update.setCourierName("System");
        update.setStatus("DELIVERED");

        courierUpdateRepository.save(update);

        kafkaTemplate.send(
                "delivery-updates",
                orderId + ":System:DELIVERED"
        );
    }
}