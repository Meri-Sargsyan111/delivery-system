package com.example.trackingservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Local copy of courier-service's courier-location-updates event - consumed to maintain
 * live TrackingState (position, ETA, remaining distance, route progress) and rebroadcast
 * an enriched update over this service's own per-order WebSocket topic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierLocationEvent {

    private Long orderId;
    private Long courierId;
    private UUID courierUserId;
    private double latitude;
    private double longitude;
    private double bearing;
    private double speedKmh;
    private Instant timestamp;
    private String vehicleType;
}
