package com.example.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for POST /orders/internal/from-payment - called only by payment-service
 * after a payment has been verified as SUCCEEDED (see InternalServiceTokenFilter for how
 * this endpoint is protected). Unlike CreateOrderRequest, customerId is always required
 * here: there's no JWT on this call path to resolve it from, so payment-service supplies
 * the id it already captured from the customer's original JWT-authenticated POST /payments.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderFromPaymentRequest {

    @NotNull
    private UUID customerId;

    @NotNull
    private UUID paymentId;

    @NotBlank
    @Size(max = 255)
    private String fromAddress;

    @NotBlank
    @Size(max = 255)
    private String toAddress;

    @Size(max = 500)
    private String packageDescription;

    @NotNull
    @Positive
    private Double weightKg;
}