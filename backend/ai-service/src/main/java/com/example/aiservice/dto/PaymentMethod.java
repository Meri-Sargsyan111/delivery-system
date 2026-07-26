package com.example.aiservice.dto;

/**
 * Mirrors order-service's own PaymentMethod enum. ai-service is a separate deployable
 * with no shared module, so it keeps its own copy rather than a cross-service dependency.
 */
public enum PaymentMethod {
    CASH,
    CARD
}