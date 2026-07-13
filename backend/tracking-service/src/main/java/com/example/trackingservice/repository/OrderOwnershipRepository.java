package com.example.trackingservice.repository;

import com.example.trackingservice.entity.OrderOwnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderOwnershipRepository extends JpaRepository<OrderOwnership, Long> {
}