package com.example.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUpdateEvent {

    private Long orderId;
    private String courierName;
    private String status;

    /** The assigned courier's linked auth-service user id, when known. */
    private UUID courierUserId;
}
