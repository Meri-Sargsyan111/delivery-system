package com.example.orderservice.controller;

import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.repository.DeliveryOrderRepository;
import com.example.orderservice.ordel.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DeliveryOrderRepository orderRepository;

    @PostMapping
    public String createOrder(@RequestBody Map<String, String> body) {
        DeliveryOrder order = new DeliveryOrder();
        order.setCustomerName(body.get("customerName"));
        order.setFromAddress(body.get("fromAddress"));
        order.setToAddress(body.get("toAddress"));
        order.setStatus(OrderStatus.CREATED);

        orderRepository.save(order);

        String message = order.getId() + ":" + order.getCustomerName() + ":" + order.getToAddress();
        kafkaTemplate.send("new-orders", message);

        return "Order created with ID: " + order.getId();
    }

    @GetMapping
    public List<DeliveryOrder> getAllOrders() {
        return orderRepository.findAll();
    }

    @GetMapping("/{id}")
    public DeliveryOrder getOrderById(@PathVariable Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @PutMapping("/{id}/assign")
    public String assignOrder(@PathVariable Long id) {
        DeliveryOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(OrderStatus.ASSIGNED);
        orderRepository.save(order);

        String message = order.getId() + ":" + order.getCustomerName() + ":" + order.getStatus();
        kafkaTemplate.send("order-assigned", message);

        return "Order assigned with ID: " + order.getId();
    }

    @GetMapping("/search")
    public List<DeliveryOrder> searchOrders(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) OrderStatus status) {

        if (customerName != null) {
            return orderRepository.findByCustomerName(customerName);
        }

        if (status != null) {
            return orderRepository.findByStatus(status);
        }

        return orderRepository.findAll();
    }

    @PutMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Long id) {
        DeliveryOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        String message = order.getId() + ":" + order.getCustomerName() + ":" + order.getStatus();
        kafkaTemplate.send("order-cancelled", message);

        return "Order cancelled with ID: " + order.getId();
    }
}