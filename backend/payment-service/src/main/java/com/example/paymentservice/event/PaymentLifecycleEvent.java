package com.example.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published to payment-events on CREATED/SUCCEEDED/FAILED/REFUNDED. Consumed by
 * order-service (REFUNDED -> cancel the order, see PaymentEventConsumer there) and
 * notification-service (customer/admin notifications).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLifecycleEvent {

    private UUID paymentId;
    private Long orderId;
    private UUID customerId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime timestamp;
}
