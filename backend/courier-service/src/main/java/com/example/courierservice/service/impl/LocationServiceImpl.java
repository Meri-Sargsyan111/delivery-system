package com.example.courierservice.service.impl;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendLocation(CourierLocation location) {
        log.debug("Broadcasting location for orderId: {}, lat={}, lon={}",
                location.getOrderId(), location.getLatitude(), location.getLongitude());
        messagingTemplate.convertAndSend("/topic/location", location);
    }
}
