package com.example.courierservice.repository;

import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.entity.Courier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, Long> {

    List<Courier> findByStatus(CourierStatus status);

    Page<Courier> findByStatus(CourierStatus status, Pageable pageable);

    boolean existsByStatus(CourierStatus status);

    Optional<Courier> findByUserId(UUID userId);
}