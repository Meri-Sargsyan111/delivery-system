package com.example.orderservice.dto;

import com.example.orderservice.order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Minimal, non-PII projection of an order's status, used by the public
 * GET /orders/{id}/status endpoint that courier-service calls synchronously
 * (no customerName/addresses/customerPhone here - only what's needed to gate
 * start-delivery/deliver/rating, plus the two owner ids for ownership checks).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusView {

    private Long id;
    private OrderStatus status;
    private UUID customerUserId;
    private UUID courierUserId;
}