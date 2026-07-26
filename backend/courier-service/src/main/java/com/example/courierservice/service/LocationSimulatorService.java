package com.example.courierservice.service;

import java.util.UUID;

/**
 * Service interface for simulating courier movement along a real driving route.
 *
 * <p>Implementations periodically publish location updates so that the system
 * can track a courier's position in real time without requiring a physical device.
 * Supports multiple concurrent orders - one simulated courier per active delivery.
 */
public interface LocationSimulatorService {

    /**
     * Advances every currently-tracked order's simulated courier along its real route
     * and publishes an updated location for each (a no-op for orders with no active
     * simulation). Intended to be triggered on a fixed schedule (e.g. every 3 seconds).
     */
    void simulateMovement();

    /**
     * Begins publishing simulated location updates for the given order, walking the
     * real driving route fetched from tracking-service. A no-op (logged, not thrown) if
     * that route can't be fetched - the caller (startDelivery) must still succeed even
     * when live tracking can't start.
     *
     * @param orderId the order that just entered {@code IN_PROGRESS}
     * @param courierId the courier record id assigned to this order
     * @param courierUserId the assigned courier's linked auth-service user id, if known
     */
    void startTracking(Long orderId, Long courierId, UUID courierUserId);

    /**
     * Stops publishing location updates for the given order, if it is currently being
     * tracked. A no-op otherwise.
     *
     * @param orderId the order that just left {@code IN_PROGRESS} (delivered or cancelled)
     */
    void stopTracking(Long orderId);
}
