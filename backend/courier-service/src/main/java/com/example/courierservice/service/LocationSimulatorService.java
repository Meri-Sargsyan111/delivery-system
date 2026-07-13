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
     * publishes the updated {@link com.example.courierservice.dto.CourierLocation}
     * for whichever order is currently being tracked (a no-op if none is).
     *
     * <p>Intended to be triggered on a fixed schedule (e.g. every 3 seconds).
     */
    void simulateMovement();

    /**
     * Begins publishing simulated location updates for the given order,
     * restarting the route from its first point.
     *
     * @param orderId the order that just entered {@code IN_PROGRESS}
     */
    void startTracking(Long orderId);

    /**
     * Stops publishing location updates for the given order, if it is the
     * order currently being tracked. A no-op otherwise.
     *
     * @param orderId the order that just left {@code IN_PROGRESS} (delivered or cancelled)
     */
    void stopTracking(Long orderId);
}
