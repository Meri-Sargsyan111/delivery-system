package com.example.orderservice.controller;

import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.ordel.OrderStatus;
import com.example.orderservice.repository.DeliveryOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final DeliveryOrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping
    public String createOrder(@RequestBody Map<String, String> body) {

        DeliveryOrder order = new DeliveryOrder();

        order.setCustomerName(body.get("customerName"));
        order.setFromAddress(body.get("fromAddress"));
        order.setToAddress(body.get("toAddress"));
        order.setStatus(OrderStatus.CREATED);

        orderRepository.save(order);

        String message =
                order.getId() + ":" +
                        order.getCustomerName() + ":" +
                        order.getToAddress();

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

    @PutMapping("/{id}/assign")
    public String assignOrder(@PathVariable Long id) {

        DeliveryOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(OrderStatus.ASSIGNED);
        orderRepository.save(order);

        return "Order assigned with ID: " + order.getId();
    }

    @PutMapping("/{id}/deliver")
    public String deliverOrder(@PathVariable Long id) {

        DeliveryOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        return "Order delivered with ID: " + order.getId();
    }

    @PutMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Long id) {

        DeliveryOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        return "Order cancelled with ID: " + order.getId();
    }
}