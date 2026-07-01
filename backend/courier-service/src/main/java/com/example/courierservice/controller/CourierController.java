package com.example.courierservice.controller;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.service.CourierService;
import com.example.courierservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/courier")
public class CourierController {

    private final CourierService courierService;
    private final LocationService locationService;

    @PutMapping("/start/{orderId}")
    public String startDelivery(@PathVariable Long orderId) {
        log.info("PUT /courier/start/{} - starting delivery", orderId);
        return courierService.startDelivery(orderId);
    }

    @PutMapping("/deliver/{orderId}")
    public String markAsDelivered(@PathVariable Long orderId) {
        log.info("PUT /courier/deliver/{} - marking as delivered", orderId);
        return courierService.markAsDelivered(orderId);
    }

    @PostMapping("/location")
    public void sendLocation(@RequestBody CourierLocation location) {
        locationService.sendLocation(location);
    }
}