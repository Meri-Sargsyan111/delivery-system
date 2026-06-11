package com.example.courierservice.controller;

import com.example.courierservice.CourierUpdate;
import com.example.courierservice.CourierUpdateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/courier")
@RequiredArgsConstructor
public class CourierController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CourierUpdateRepository courierUpdateRepository;

    @PostMapping("/update")
    public String updateStatus(@RequestBody Map<String, String> body) {
        CourierUpdate update = new CourierUpdate();
        update.setOrderId(Long.parseLong(body.get("orderId")));
        update.setCourierName(body.get("courierName"));
        update.setStatus(body.get("status"));

        courierUpdateRepository.save(update);

        String message = update.getOrderId() + ":" + update.getCourierName() + ":" + update.getStatus();
        kafkaTemplate.send("delivery-updates", message);

        return "Status updated: " + update.getStatus();
    }

    @PutMapping("/deliver/{orderId}")
    public String markAsDelivered(@PathVariable Long orderId) {
        CourierUpdate update = new CourierUpdate();
        update.setOrderId(orderId);
        update.setCourierName("System");
        update.setStatus("DELIVERED");

        courierUpdateRepository.save(update);

        String message = orderId + ":System:DELIVERED";
        kafkaTemplate.send("delivery-updates", message);

        return "Order delivered: " + orderId;
    }
}