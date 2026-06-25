package com.example.courierservice.service.impl;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.service.LocationService;
import com.example.courierservice.service.LocationSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.example.courierservice.service.DeliveryStatusService;
@Service
@RequiredArgsConstructor
public class LocationSimulationServiceImpl implements LocationSimulationService {

    private final LocationService locationService;
    private final DeliveryStatusService deliveryStatusService;
    @Async
    @Override
    public void simulateTrip(Long orderId) {

        double[][] route = {
                {40.1772, 44.5035},
                {40.1780, 44.5042},
                {40.1788, 44.5050},
                {40.1796, 44.5058},
                {40.1805, 44.5067},
                {40.1814, 44.5075},
                {40.1823, 44.5084},
                {40.1832, 44.5092}
        };

        for (double[] point : route) {

            System.out.println(
                    "Sending location: " +
                            point[0] + ", " + point[1]
            );

            CourierLocation location = new CourierLocation(
                    orderId,
                    point[0],
                    point[1]
            );

            locationService.sendLocation(location);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        deliveryStatusService.completeDelivery(orderId);
            }
        }
