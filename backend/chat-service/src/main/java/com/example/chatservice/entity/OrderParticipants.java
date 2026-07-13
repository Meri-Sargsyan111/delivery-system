package com.example.chatservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Local read-model projection of who may participate in an order's chat, built entirely
 * from Kafka events (new-orders, delivery-updates) - chat-service does not own order data,
 * so this mirrors tracking-service's OrderOwnership projection exactly (see that class for
 * the full rationale): no synchronous call to order-service, no new cross-service coupling.
 *
 * orderStatus is tracked here too (not just customer/courier ids) because chat sending is
 * disabled once an order reaches a terminal state (DELIVERED/CANCELLED) - see ChatServiceImpl.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "order_participants")
public class OrderParticipants {

    @Id
    private Long orderId;

    private UUID customerUserId;

    private UUID courierUserId;

    private String orderStatus;
}