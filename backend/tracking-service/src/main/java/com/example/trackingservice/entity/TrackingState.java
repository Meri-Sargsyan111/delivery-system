package com.example.trackingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Live tracking state for one order - position, ETA, remaining distance/duration, route
 * progress, and delivery phase. Additive alongside the existing TrackingEvent status
 * timeline (still populated exactly as before by TrackingConsumer) rather than a
 * replacement - GET /tracking/{orderId} keeps returning exactly what it always has.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tracking_state")
public class TrackingState {

    @Id
    private Long orderId;

    private Long courierId;
    private UUID courierUserId;

    /** Kept so geocoding can be retried lazily if it failed at order-creation time. */
    @Column(length = 500)
    private String fromAddress;
    @Column(length = 500)
    private String toAddress;

    private Double pickupLat;
    private Double pickupLng;
    private Double destinationLat;
    private Double destinationLng;

    private Double currentLat;
    private Double currentLng;
    private double bearing;
    private double speedKmh;

    private Double remainingDistanceKm;
    private Double remainingDurationMin;
    private double routeProgressPercent;

    /**
     * Index into the cached route polyline the last location update matched, used to
     * bound the next forward-only nearest-point search (see LiveLocationConsumer) so
     * progress can never jump backward on a route with loops/parallel segments.
     */
    private int lastMatchedSegmentIndex;

    /** True when the last matched position was farther than the deviation threshold from the route. */
    private boolean deviated;

    @Enumerated(EnumType.STRING)
    private DeliveryPhase phase;

    private LocalDateTime phaseChangedAt;

    /** Mirrors courier-service's VehicleType by name (String, not a shared enum - see Courier). */
    private String vehicleType;

    private LocalDateTime eta;

    private Double routeDistanceKm;
    private Double routeDurationMin;

    @Column(columnDefinition = "TEXT")
    private String routeGeometryJson;

    private LocalDateTime lastUpdate;
}
