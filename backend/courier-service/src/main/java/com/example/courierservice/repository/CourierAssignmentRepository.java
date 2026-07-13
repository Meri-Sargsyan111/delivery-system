package com.example.courierservice.repository;

import com.example.courierservice.entity.CourierAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourierAssignmentRepository extends JpaRepository<CourierAssignment, Long> {

    Optional<CourierAssignment> findByOrderId(Long orderId);
}