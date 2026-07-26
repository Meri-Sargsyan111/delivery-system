package com.example.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Local copy of payment-service's payment-events payload - order-service only cares
 * about REFUNDED (see PaymentEventConsumer), so most fields are unused here, but the
 * full shape is kept for forward-compatibility with schema evolution.
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
