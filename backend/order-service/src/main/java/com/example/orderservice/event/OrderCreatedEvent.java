package com.example.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    private Long orderId;
    private String customerName;
    private String toAddress;

    /** The owning customer's auth-service user id, for downstream ownership projections. */
    private UUID customerUserId;
}
