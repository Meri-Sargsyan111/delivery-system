package com.example.trackingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persistent routing cache keyed by rounded from/to coordinates (~11m precision at 4
 * decimal places - plenty for cache-hit purposes, addresses don't move) so the same
 * pickup/destination pair never re-queries OSRM within the cache TTL (see
 * routing.cache-ttl-hours). geometryJson is the route polyline as a JSON array of
 * [lat, lng] pairs.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "route_cache", indexes = {
        @Index(name = "idx_route_cache_coords", columnList = "fromLat,fromLng,toLat,toLng")
})
public class RouteCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double fromLat;
    private double fromLng;
    private double toLat;
    private double toLng;

    private double distanceKm;
    private double durationMinutes;

    @Column(columnDefinition = "TEXT")
    private String geometryJson;

    private LocalDateTime createdAt;
}
