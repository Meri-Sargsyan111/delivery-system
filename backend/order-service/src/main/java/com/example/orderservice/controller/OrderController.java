package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.DeliveryOrderResponse;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final OrderMapper orderMapper;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /orders - received request to create new order");
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping
    public ResponseEntity<Page<DeliveryOrderResponse>> getOrders(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrders(pageable).map(orderMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryOrderResponse> getOrderById(@PathVariable Long id) {
        log.info("GET /orders/{}", id);
        return ResponseEntity.ok(orderMapper.toResponse(orderService.getOrderById(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DeliveryOrderResponse>> searchOrders(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("GET /orders/search - customerName provided: {}, status: {}", customerName != null, status);
        return ResponseEntity.ok(orderService.searchOrders(customerName, status, pageable).map(orderMapper::toResponse));
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<OrderResponse> assignOrder(@PathVariable Long id) {
        log.info("PUT /orders/{}/assign", id);
        return ResponseEntity.ok(orderService.assignOrder(id));
    }

    @PutMapping("/{id}/deliver")
    public ResponseEntity<OrderResponse> deliverOrder(@PathVariable Long id) {
        log.info("PUT /orders/{}/deliver", id);
        return ResponseEntity.ok(orderService.deliverOrder(id));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        log.info("PUT /orders/{}/cancel", id);
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

}
