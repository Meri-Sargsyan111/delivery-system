package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /orders - received request to create new order");
        return orderService.createOrder(request);
    }

    @GetMapping
    public Page<DeliveryOrder> getOrders(@PageableDefault(size = 20) Pageable pageable) {
        return orderService.getOrders(pageable);
    }

    @GetMapping("/{id}")
    public DeliveryOrder getOrderById(@PathVariable Long id) {
        log.info("GET /orders/{}", id);
        return orderService.getOrderById(id);
    }

    @GetMapping("/search")
    public Page<DeliveryOrder> searchOrders(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("GET /orders/search - customerName provided: {}, status: {}", customerName != null, status);
        return orderService.searchOrders(customerName, status, pageable);
    }

    @PutMapping("/{id}/assign")
    public OrderResponse assignOrder(@PathVariable Long id) {
        log.info("PUT /orders/{}/assign", id);
        return orderService.assignOrder(id);
    }

    @PutMapping("/{id}/deliver")
    public OrderResponse deliverOrder(@PathVariable Long id) {
        log.info("PUT /orders/{}/deliver", id);
        return orderService.deliverOrder(id);
    }

    @PutMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long id) {
        log.info("PUT /orders/{}/cancel", id);
        return orderService.cancelOrder(id);
    }

}
