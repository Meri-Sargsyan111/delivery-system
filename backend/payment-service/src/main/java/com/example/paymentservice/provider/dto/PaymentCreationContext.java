package com.example.paymentservice.provider.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreationContext(UUID paymentId, BigDecimal amount, String currency, String description) {
}
