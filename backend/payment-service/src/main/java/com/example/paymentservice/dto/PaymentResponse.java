package com.example.paymentservice.dto;

import com.example.paymentservice.entity.PaymentMethodType;
import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Returned from POST /payments (and GET /payments/{id}/verify) - clientSecret is what a
 * frontend would hand to the provider's own JS SDK (e.g. Stripe.js/Elements) to actually
 * complete the payment; null once the payment has moved past PROCESSING.
 */
public record PaymentResponse(
        UUID id,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        PaymentProvider provider,
        PaymentMethodType paymentMethod,
        String clientSecret,
        Long orderId,
        LocalDateTime createdAt
) {
}
