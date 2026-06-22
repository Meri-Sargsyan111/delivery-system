package com.example.orderservice.repository;

import com.example.orderservice.ordel.OrderStatus;
import com.example.orderservice.entity.DeliveryOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    List<DeliveryOrder> findByCustomerName(String customerName);

    List<DeliveryOrder> findByStatus(OrderStatus status);
}