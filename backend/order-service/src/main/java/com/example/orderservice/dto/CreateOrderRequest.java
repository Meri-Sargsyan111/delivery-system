package com.example.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank
    @Size(max = 100)
    private String customerName;

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