package com.example.courierservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUpdateEvent {

    private Long orderId;
    private String courierName;
    private String status;
}
