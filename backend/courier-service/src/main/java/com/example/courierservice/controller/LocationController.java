package com.example.courierservice.controller;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/location")
public class LocationController {

    private final LocationService locationService;

    @PreAuthorize("hasRole('ADMIN') or hasRole('COURIER')")
    @PostMapping("/update")
    public ResponseEntity<Void> updateLocation(@RequestBody CourierLocation location) {
        locationService.sendLocation(location);
        return ResponseEntity.ok().build();
    }
}
