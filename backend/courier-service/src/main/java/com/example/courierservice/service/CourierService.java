package com.example.courierservice.service;

import com.example.courierservice.entity.CourierUpdate;
import com.example.courierservice.repository.CourierUpdateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierUpdateRepository courierUpdateRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LocationSimulationService locationSimulationService;

    public String startDelivery(Long orderId) {

        locationSimulationService.simulateTrip(orderId);

        return "Delivery started";
    }

    public String markAsDelivered(Long orderId) {

        CourierUpdate update = new CourierUpdate();

        update.setOrderId(orderId);
        update.setCourierName("System");
        update.setStatus("DELIVERED");

        courierUpdateRepository.save(update);

        String message =
                orderId + ":System:DELIVERED";

        kafkaTemplate.send("delivery-updates", message);

        return "Delivery completed";
    }
}