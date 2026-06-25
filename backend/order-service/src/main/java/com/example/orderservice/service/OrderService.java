package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.repository.DeliveryOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final DeliveryOrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public String createOrder(CreateOrderRequest request) {

        DeliveryOrder order = new DeliveryOrder();

        order.setCustomerName(request.getCustomerName());
        order.setFromAddress(request.getFromAddress());
        order.setToAddress(request.getToAddress());
        order.setStatus(OrderStatus.CREATED);

        orderRepository.save(order);

        String message =
                order.getId() + ":" +
                        order.getCustomerName() + ":" +
                        order.getToAddress();

        kafkaTemplate.send("new-orders", message);

        return "Order created with ID: " + order.getId();
    }

    public List<DeliveryOrder> getAllOrders() {
        return orderRepository.findAll();
    }

    public DeliveryOrder getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public List<DeliveryOrder> searchOrders(String customerName, OrderStatus status) {

        if (customerName != null) {
            return orderRepository.findByCustomerName(customerName);
        }

        if (status != null) {
            return orderRepository.findByStatus(status);
        }

        return orderRepository.findAll();
    }

    public String assignOrder(Long id) {

        DeliveryOrder order = getOrderById(id);

        order.setStatus(OrderStatus.ASSIGNED);
        orderRepository.save(order);

        return "Order assigned with ID: " + order.getId();
    }

    public String deliverOrder(Long id) {

        DeliveryOrder order = getOrderById(id);

        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        return "Order delivered with ID: " + order.getId();
    }

    public String cancelOrder(Long id) {

        DeliveryOrder order = getOrderById(id);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        return "Order cancelled with ID: " + order.getId();
    }
}