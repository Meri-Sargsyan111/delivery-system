package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderFromPaymentRequest;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.DeliveryOrderResponse;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.OrderStatusView;
import com.example.orderservice.dto.UnassignOrderRequest;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /**
     * Minimal, unauthenticated internal projection used only by courier-service's
     * synchronous status/ownership check (see SecurityConfig). Deliberately excludes
     * customerName/addresses/customerPhone - see OrderStatusView.
     */
    @GetMapping("/{id:\\d+}/status")
    public ResponseEntity<OrderStatusView> getOrderStatus(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderByIdInternal(id));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /orders - received request to create new order");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(request));
    }

    /**
     * Called only by payment-service after a payment has been verified as SUCCEEDED - no
     * end-user JWT exists on this call path, so this stays outside the normal
     * @PreAuthorize chain and is instead protected by InternalServiceTokenFilter (see
     * SecurityConfig). Never intended to be reachable from a browser/API client directly.
     */
    @PostMapping("/internal/from-payment")
    public ResponseEntity<OrderResponse> createOrderFromPayment(@Valid @RequestBody CreateOrderFromPaymentRequest request) {
        log.info("POST /orders/internal/from-payment - paymentId={}", request.getPaymentId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrderFromPayment(request));
    }

    @GetMapping
    public ResponseEntity<Page<DeliveryOrderResponse>> getOrders(
            @PageableDefault(
                    size = 20,
                    sort = "id",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable) {

        return ResponseEntity.ok(orderService.getOrders(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryOrderResponse> getOrderById(@PathVariable Long id) {
        log.info("GET /orders/{}", id);
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DeliveryOrderResponse>> searchOrders(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("GET /orders/search - customerName provided: {}, status: {}", customerName != null, status);
        return ResponseEntity.ok(orderService.searchOrders(customerName, status, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/assign")
    public ResponseEntity<OrderResponse> assignOrder(@PathVariable Long id,
                                                      @RequestParam Long courierId) {
        log.info("PUT /orders/{}/assign - courierId={}", id, courierId);
        return ResponseEntity.ok(orderService.assignOrder(id, courierId));
    }

    /**
     * Called only by courier-service - when a courier rejects an assignment, or its
     * offer-timeout sweep gives up on one unanswered - with no end-user JWT to present
     * (service-to-service call; the courier's own accept/reject HTTP request already
     * authenticated against courier-service). Protected by InternalServiceTokenFilter (see
     * SecurityConfig), same shared secret as every other internal endpoint.
     */
    @PutMapping("/internal/{id}/unassign")
    public ResponseEntity<OrderResponse> unassignOrder(
            @PathVariable Long id, @Valid @RequestBody UnassignOrderRequest request) {
        log.info("PUT /orders/internal/{}/unassign - courierId={}", id, request.getCourierId());
        return ResponseEntity.ok(orderService.unassignOrder(id, request));
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
