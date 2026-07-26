package com.example.paymentservice.provider.dto;

import com.example.paymentservice.entity.PaymentStatus;

public record RefundResult(String providerRefundId, PaymentStatus status, String rawResponseJson) {
}
