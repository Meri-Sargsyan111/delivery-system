package com.example.chatservice.event;

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
    private UUID courierUserId;
}