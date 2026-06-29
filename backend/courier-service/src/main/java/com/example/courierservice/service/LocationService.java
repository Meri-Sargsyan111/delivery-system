package com.example.courierservice.service;

import com.example.courierservice.dto.CourierLocation;

/**
 * Service interface for publishing real-time courier location updates.
 *
 * <p>Abstracts the mechanism by which a courier's current geographic position
 * is transmitted to the system — for example, via a Kafka topic or WebSocket
 * broadcast — so that clients can track deliveries in real time.
 */
public interface LocationService {

    /**
     * Sends a courier's current location to the appropriate messaging channel.
     *
     * <p>The location payload is forwarded to subscribed consumers (e.g., the
     * tracking dashboard or mobile clients) without blocking the caller.
     *
     * @param location the {@link CourierLocation} object containing the courier's
     *                 identifier and current geographic coordinates
     */
    void sendLocation(CourierLocation location);
}
