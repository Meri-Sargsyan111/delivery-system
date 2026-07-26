package com.example.paymentservice.dto;

import com.example.paymentservice.entity.PaymentMethodType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Note what's deliberately NOT here: amount, currency, delivery addresses. Those all
 * come from claiming estimateId server-side (see AiServiceClient) - the client can never
 * supply or influence the amount charged.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotNull
    private UUID estimateId;

    @NotNull
    private PaymentMethodType paymentMethod;

    /** Admin-only: the customer this payment is being created on behalf of. Ignored for CUSTOMER callers - see OrderCustomerResolver's equivalent pattern in order-service. */
    private UUID customerId;
}
