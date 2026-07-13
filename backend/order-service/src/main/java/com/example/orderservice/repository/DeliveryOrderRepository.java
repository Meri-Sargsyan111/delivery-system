package com.example.orderservice.repository;

import com.example.orderservice.entity.DeliveryOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long>,
        JpaSpecificationExecutor<DeliveryOrder> {

    Page<DeliveryOrder> findByCustomerUserId(UUID customerUserId, Pageable pageable);

    Page<DeliveryOrder> findByCourierUserId(UUID courierUserId, Pageable pageable);
}