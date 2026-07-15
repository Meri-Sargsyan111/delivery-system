package com.example.trackingservice.repository;

import com.example.trackingservice.entity.OrderOwnership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderOwnershipRepository extends JpaRepository<OrderOwnership, Long> {
}