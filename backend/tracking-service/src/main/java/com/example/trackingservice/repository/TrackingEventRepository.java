package com.example.trackingservice.repository;

import com.example.trackingservice.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {
    List<TrackingEvent> findByOrderId(Long orderId);
}