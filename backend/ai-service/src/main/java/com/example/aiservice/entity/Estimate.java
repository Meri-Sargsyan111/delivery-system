package com.example.aiservice.entity;

import com.example.aiservice.dto.RecommendedVehicle;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A frozen delivery-price quote, persisted so payment-service can charge the exact
 * amount the customer actually saw and confirmed rather than trusting a client-supplied
 * amount or re-computing pricing itself (which would hit live, uncached Nominatim/OSRM
 * calls and could legitimately return a different number on a second call). Single-use
 * (see EstimateRepository.claim) and short-lived (see expiresAt) - not a general-purpose
 * quote history.
 */
@Data
@Entity
@Table(name = "estimates")
public class Estimate {

    @Id
    private UUID id;

    private String fromAddress;
    private String toAddress;
    private String packageDescription;
    private Double weightKg;

    private Double estimatedPrice;
    private String estimatedDeliveryTime;

    @Enumerated(EnumType.STRING)
    private RecommendedVehicle recommendedVehicle;

    private String currency;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    private boolean consumed;
    private LocalDateTime consumedAt;
}
