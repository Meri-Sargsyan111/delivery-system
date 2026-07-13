package com.example.orderservice.entity;

import com.example.orderservice.order.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "delivery_orders")
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;

    private String fromAddress;

    private String toAddress;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Long courierId;

    private String customerPhone;

    /**
     * The auth-service user id (JWT "sub") of the customer who created this order.
     * Set once, from SecurityContext, at creation - never trusted from the client.
     * Null on orders that existed before ownership tracking was introduced; those
     * legacy rows are intentionally admin-only (see OrderServiceImpl) rather than
     * guessed-assigned to any user.
     */
    private UUID customerUserId;

    /**
     * The auth-service user id of the courier currently assigned to this order,
     * propagated from courier-service at assignment time (see CourierServiceClient).
     * Distinct from courierId, which is courier-service's own record id and carries
     * no provable link to the authenticated user operating that courier.
     */
    private UUID courierUserId;
}