package com.example.trackingservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Trimmed local view of order-service's GET /orders/{id}/status projection - mirrors
 * courier-service's own copy of the same DTO. Not shared as a common module, per the
 * project's microservice boundaries. Ignores unknown properties for forward-compatibility.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteOrderView {

    private Long id;
    private String status;
    private UUID customerUserId;
    private UUID courierUserId;
}