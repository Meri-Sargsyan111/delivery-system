package com.example.paymentservice.entity;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    AUTHORIZED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    REFUNDED,
    EXPIRED
}
