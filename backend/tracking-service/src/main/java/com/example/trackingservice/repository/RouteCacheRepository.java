package com.example.trackingservice.repository;

import com.example.trackingservice.entity.RouteCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RouteCacheRepository extends JpaRepository<RouteCache, Long> {

    Optional<RouteCache> findFirstByFromLatAndFromLngAndToLatAndToLngAndCreatedAtAfter(
            double fromLat, double fromLng, double toLat, double toLng, LocalDateTime createdAfter);
}
