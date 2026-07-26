package com.example.aiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstimateRequest {

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

    @NotNull
    private PaymentMethod paymentMethod;
}