package com.example.courierservice.controller;

import com.example.courierservice.entity.CourierUpdate;
import com.example.courierservice.repository.CourierUpdateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/courier")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class CourierController {

    private final CourierUpdateRepository courierUpdateRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @GetMapping("/test")
    public String test() {
        return "NVARD TEST 123";
    }

    @PutMapping("/deliver/{orderId}")
    public String markAsDelivered(@PathVariable Long orderId) {

        CourierUpdate update = new CourierUpdate();

        update.setOrderId(orderId);
        update.setCourierName("System");
        update.setStatus("DELIVERED");

        courierUpdateRepository.save(update);

        String message =
                orderId + ":System:DELIVERED";

        kafkaTemplate.send("delivery-updates", message);

        return "OK";
    }
}