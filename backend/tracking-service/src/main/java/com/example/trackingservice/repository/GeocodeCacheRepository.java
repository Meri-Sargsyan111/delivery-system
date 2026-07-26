package com.example.trackingservice.repository;

import com.example.trackingservice.entity.GeocodeCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, Long> {

    Optional<GeocodeCache> findByNormalizedAddress(String normalizedAddress);
}
