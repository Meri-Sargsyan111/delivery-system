package com.example.orderservice.repository;

import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long>,
        JpaSpecificationExecutor<DeliveryOrder> {
}
