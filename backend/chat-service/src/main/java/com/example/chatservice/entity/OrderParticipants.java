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

    /**
     * Denormalized display name, captured from the same Kafka events that already deliver
     * these ids (OrderCreatedEvent/DeliveryUpdateEvent) - used only by the conversation-list
     * endpoint (see ChatServiceImpl.getConversations), not by any authorization check. Null
     * for participants recorded before this field existed, or before the corresponding event
     * has arrived yet (e.g. courierName before a courier is assigned).
     */
    private String customerName;

    private String courierName;

    /** Pre-existing constructor shape, kept alongside the Lombok-generated 6-arg one so
     *  the consumers' and tests' existing call sites don't need to change. */
    public OrderParticipants(Long orderId, UUID customerUserId, UUID courierUserId, String orderStatus) {
        this.orderId = orderId;
        this.customerUserId = customerUserId;
        this.courierUserId = courierUserId;
        this.orderStatus = orderStatus;
    }
}