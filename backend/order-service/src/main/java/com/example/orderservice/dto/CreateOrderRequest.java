package com.example.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    /**
     * Only meaningful for an ADMIN caller (the customer they picked) - required in that
     * case, enforced by OrderCustomerResolver rather than bean validation here, since
     * "required" depends on the caller's role. Never read for a CUSTOMER caller: see
     * OrderCustomerResolver.
     */
    private UUID customerId;

    @NotBlank
    @Size(max = 255)
    private String fromAddress;

    @NotBlank
    @Size(max = 255)
    private String toAddress;

    @NotBlank
    @Size(max = 20)
    @Pattern(
            regexp = "^(?=(?:.*\\d){6,})\\+?[0-9()\\-\\s]+$",
            message = "customerPhone must contain only digits, spaces, hyphens, parentheses, and an optional leading +, with at least 6 digits"
    )
    private String customerPhone;
}