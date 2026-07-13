package com.example.trackingservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Local copy of order-service's new-orders event, consumed only to learn the owning
 * customer's user id for the local OrderOwnership projection - see OrderOwnership.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    private Long orderId;
    private String customerName;
    private String toAddress;
    private UUID customerUserId;
}