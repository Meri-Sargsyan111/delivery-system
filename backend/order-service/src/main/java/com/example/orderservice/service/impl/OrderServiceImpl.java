package com.example.orderservice.service.impl;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.exception.EntityNotFoundException;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.repository.DeliveryOrderRepository;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final DeliveryOrderRepository orderRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final OrderMapper orderMapper;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        try {
            DeliveryOrder order = orderMapper.toEntity(request);
            order.setStatus(OrderStatus.CREATED);

            orderRepository.save(order);

            kafkaTemplate.send("new-orders",
                    new OrderCreatedEvent(
                            order.getId(),
                            order.getCustomerName(),
                            order.getToAddress()));

            log.info("Order {} created and published to Kafka", order.getId());
            return new OrderResponse(order.getId(), "Order created");

        } catch (Exception e) {
            log.error("Failed to create order", e);
            throw e;
        }
    }

    @Override
    public Page<DeliveryOrder> getOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Override
    public DeliveryOrder getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Order not found with id: {}", id);
                    return new EntityNotFoundException("Order not found with id: " + id);
                });
    }

    @Override
    public Page<DeliveryOrder> searchOrders(String customerName, OrderStatus status, Pageable pageable) {
        return orderRepository.findAll(OrderSpecification.withFilters(customerName, status), pageable);
    }

    @Override
    public OrderResponse assignOrder(Long id) {
        DeliveryOrder order = getOrderById(id);
        order.setStatus(OrderStatus.ASSIGNED);
        orderRepository.save(order);
        log.info("Order {} status changed to ASSIGNED", id);
        return new OrderResponse(order.getId(), "Order assigned");
    }

    @Override
    public OrderResponse deliverOrder(Long id) {
        DeliveryOrder order = getOrderById(id);
        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);
        log.info("Order {} status changed to DELIVERED", id);
        return new OrderResponse(order.getId(), "Order delivered");
    }

    @Override
    public OrderResponse cancelOrder(Long id) {
        DeliveryOrder order = getOrderById(id);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order {} status changed to CANCELLED", id);
        return new OrderResponse(order.getId(), "Order cancelled");
    }
}
