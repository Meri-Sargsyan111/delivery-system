package com.example.courierservice.service;

import com.example.courierservice.dto.CourierLocation;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendLocation(CourierLocation location) {

        messagingTemplate.convertAndSend(
                "/topic/location",
                location
        );
    }
}