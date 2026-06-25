package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {


    private final OrderService orderService;

    @PostMapping
    public String createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping
    public List<DeliveryOrder> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/{id}")
    public DeliveryOrder getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    @GetMapping("/search")
    public List<DeliveryOrder> searchOrders(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) OrderStatus status) {

        return orderService.searchOrders(customerName, status);
    }

    @PutMapping("/{id}/assign")
    public String assignOrder(@PathVariable Long id) {
        return orderService.assignOrder(id);
    }

    @PutMapping("/{id}/deliver")
    public String deliverOrder(@PathVariable Long id) {
        return orderService.deliverOrder(id);
    }

    @PutMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }


}
