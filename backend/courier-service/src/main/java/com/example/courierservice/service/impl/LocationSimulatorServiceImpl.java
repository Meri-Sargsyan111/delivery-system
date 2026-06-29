package com.example.courierservice.service.impl;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.service.LocationService;
import com.example.courierservice.service.LocationSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationSimulatorServiceImpl implements LocationSimulatorService {

    private final LocationService locationService;

    private static final double[][] ROUTE = {
        {40.1772, 44.5035},
        {40.1780, 44.5042},
        {40.1788, 44.5050},
        {40.1796, 44.5058},
        {40.1804, 44.5066},
        {40.1812, 44.5074}
    };

    private int index = 0;

    @Override
    @Scheduled(fixedDelay = 3000)
    public void simulateMovement() {
        double[] point = ROUTE[index % ROUTE.length];
        CourierLocation location = new CourierLocation(1L, point[0], point[1]);
        log.info("Simulating courier at [{}, {}]", point[0], point[1]);
        locationService.sendLocation(location);
        index++;
    }
}
