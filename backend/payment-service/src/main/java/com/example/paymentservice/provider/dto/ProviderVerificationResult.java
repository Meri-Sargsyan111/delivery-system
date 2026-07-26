package com.example.paymentservice.provider.dto;

import com.example.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;

public record ProviderVerificationResult(
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String rawResponseJson,
        String failureReason
) {
}
