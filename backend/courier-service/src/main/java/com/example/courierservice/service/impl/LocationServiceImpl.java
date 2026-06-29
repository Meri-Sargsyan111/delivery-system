package com.example.courierservice.service.impl;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendLocation(CourierLocation location) {
        messagingTemplate.convertAndSend(
                "/topic/location",
                location
        );
    }
}
