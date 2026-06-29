package com.example.orderservice.service.impl;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.repository.DeliveryOrderRepository;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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

            return new OrderResponse(order.getId(), "Order created");

        } catch (Exception e) {
            e.printStackTrace();
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
                .orElseThrow(() -> new RuntimeException("Order not found"));
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
        return new OrderResponse(order.getId(), "Order assigned");
    }

    @Override
    public OrderResponse deliverOrder(Long id) {
        DeliveryOrder order = getOrderById(id);
        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);
        return new OrderResponse(order.getId(), "Order delivered");
    }

    @Override
    public OrderResponse cancelOrder(Long id) {
        DeliveryOrder order = getOrderById(id);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return new OrderResponse(order.getId(), "Order cancelled");
    }
}
