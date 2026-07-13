package com.example.notificationservice.event;

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

    /** The assigned courier's auth-service user id, used to target this notification. */
    private UUID courierUserId;
}
