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
 * Persistent geocoding cache so the same address is never resent to Nominatim twice -
 * required both for performance and to respect Nominatim's usage policy (max ~1 req/s).
 * Keyed by the exact address string GeocodingClient was asked to resolve, normalized
 * (trimmed, lowercased) so trivial formatting differences still hit the cache.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "geocode_cache", indexes = {
        @Index(name = "idx_geocode_cache_normalized_address", columnList = "normalizedAddress", unique = true)
})
public class GeocodeCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500, nullable = false)
    private String normalizedAddress;

    private double latitude;
    private double longitude;

    private LocalDateTime createdAt;
}
