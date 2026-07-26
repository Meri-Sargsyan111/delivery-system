package com.example.paymentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Local view of ai-service's POST /ai/estimate/{id}/claim response - only the fields
 * payment-service actually needs (the authoritative amount/currency plus the delivery
 * details order-service will need later). Ignores unknown properties for
 * forward-compatibility, same convention as every other cross-service client DTO here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstimateClaimResult(
        UUID estimateId,
        String fromAddress,
        String toAddress,
        String packageDescription,
        Double weightKg,
        BigDecimal amount,
        String currency
) {
}
