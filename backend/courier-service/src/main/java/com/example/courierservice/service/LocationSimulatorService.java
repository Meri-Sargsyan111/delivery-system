package com.example.courierservice.service;

/**
 * Service interface for simulating courier movement along a predefined route.
 *
 * <p>Implementations periodically publish location updates so that the system
 * can track a courier's position in real time without requiring a physical device.
 */
public interface LocationSimulatorService {

    /**
     * Advances the simulated courier to the next point on the route and
     * publishes the updated {@link com.example.courierservice.dto.CourierLocation}.
     *
     * <p>Intended to be triggered on a fixed schedule (e.g. every 3 seconds).
     */
    void simulateMovement();
}
