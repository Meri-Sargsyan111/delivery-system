package com.example.paymentservice.client.dto;

import java.util.UUID;

/** Request body for order-service's POST /orders/internal/from-payment - mirrors CreateOrderFromPaymentRequest there. */
public record CreateOrderFromPaymentPayload(
        UUID customerId,
        UUID paymentId,
        String fromAddress,
        String toAddress,
        String packageDescription,
        Double weightKg
) {
}
