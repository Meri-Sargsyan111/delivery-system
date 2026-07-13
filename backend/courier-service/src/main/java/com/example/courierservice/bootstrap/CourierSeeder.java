package com.example.courierservice.bootstrap;

import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.entity.Courier;
import com.example.courierservice.repository.CourierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * There is no courier onboarding UI yet, so seed a small roster on startup
 * if none exists, otherwise there would be nobody to assign to orders.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourierSeeder implements CommandLineRunner {

    private final CourierRepository courierRepository;

    @Override
    public void run(String... args) {
        if (courierRepository.count() > 0) {
            return;
        }

        List<Courier> couriers = List.of(
                new Courier(null, "Alice Johnson", CourierStatus.AVAILABLE, null, null),
                new Courier(null, "Bob Martins", CourierStatus.AVAILABLE, null, null),
                new Courier(null, "Carla Souza", CourierStatus.AVAILABLE, null, null),
                new Courier(null, "David Petrov", CourierStatus.OFFLINE, null, null)
        );

        courierRepository.saveAll(couriers);
        log.info("No couriers existed - seeded {} default couriers", couriers.size());
    }
}
