package com.example.trackingservice.repository;

import com.example.trackingservice.entity.DeliveryPhase;
import com.example.trackingservice.entity.TrackingState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TrackingStateRepository extends JpaRepository<TrackingState, Long> {

    Page<TrackingState> findByPhaseNotIn(Collection<DeliveryPhase> phases, Pageable pageable);

    List<TrackingState> findByPhaseNotIn(Collection<DeliveryPhase> phases);

    long countByPhaseNotIn(Collection<DeliveryPhase> phases);

    long countByPhaseAndPhaseChangedAtAfter(DeliveryPhase phase, LocalDateTime after);

    long countByCourierUserIdIsNotNullAndPhaseNotIn(Collection<DeliveryPhase> phases);
}
