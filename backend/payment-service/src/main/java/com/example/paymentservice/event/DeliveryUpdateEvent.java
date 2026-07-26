package com.example.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Local copy of the EXISTING delivery-updates event (order-service is the producer,
 * already consumed by five other services) - payment-service joins as its own consumer
 * group (payment-group) to react to CANCELLED (see kafka.DeliveryUpdateConsumer). Only
 * orderId/status are used here; the rest is kept for shape-completeness.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUpdateEvent {

    private Long orderId;
    private String courierName;
    private String status;
    private UUID courierUserId;
}
