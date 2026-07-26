package com.example.paymentservice.dto;

import com.example.paymentservice.entity.PaymentMethodType;
import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Full detail view for GET /payments/{id} and /payments/history - no raw provider response included (see providerResponse on the entity, kept internal/audit-only). */
public record PaymentDetailsResponse(
        UUID id,
        String transactionId,
        PaymentProvider provider,
        PaymentMethodType paymentMethod,
        PaymentStatus status,
        String currency,
        BigDecimal amount,
        UUID customerId,
        Long orderId,
        String providerReference,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String failureReason
) {
}
