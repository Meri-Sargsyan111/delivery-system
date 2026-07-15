package com.example.courierservice.service.impl;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.service.LocationService;
import com.example.courierservice.service.LocationSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationSimulatorServiceImpl implements LocationSimulatorService {

    private final LocationService locationService;

    private static final long SIMULATION_INTERVAL_MS = 3000;

    private static final double[][] ROUTE = {
        {40.1772, 44.5035},
        {40.1780, 44.5042},
        {40.1788, 44.5050},
        {40.1796, 44.5058},
        {40.1804, 44.5066},
        {40.1812, 44.5074}
    };

    private final AtomicReference<Long> activeOrderId = new AtomicReference<>();
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public void startTracking(Long orderId) {
        index.set(0);
        activeOrderId.set(orderId);
        log.info("Live tracking started for orderId={}", orderId);
    }

    @Override
    public void stopTracking(Long orderId) {
        if (activeOrderId.compareAndSet(orderId, null)) {
            log.info("Live tracking stopped for orderId={}", orderId);
        }
    }

    @Override
    @Scheduled(fixedDelay = SIMULATION_INTERVAL_MS)
    public void simulateMovement() {
        Long orderId = activeOrderId.get();
        if (orderId == null) {
            return;
        }

        double[] point = ROUTE[index.getAndIncrement() % ROUTE.length];
        CourierLocation location = new CourierLocation(orderId, point[0], point[1]);
        log.info("Simulating courier at [{}, {}] for orderId={}", point[0], point[1], orderId);
        locationService.sendLocation(location);
    }
}
