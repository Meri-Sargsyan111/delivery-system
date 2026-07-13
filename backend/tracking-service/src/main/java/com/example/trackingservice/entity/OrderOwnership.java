package com.example.trackingservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Local read-model projection of who a given order belongs to, built entirely from
 * Kafka events (new-orders, delivery-updates) rather than a synchronous call to
 * order-service - tracking-service does not own order data, so this is the smallest
 * secure way to authorize GET /tracking/{orderId} without trusting the orderId alone
 * or introducing a new cross-service call for every read.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "order_ownership")
public class OrderOwnership {

    @Id
    private Long orderId;

    private UUID customerUserId;

    private UUID courierUserId;
}