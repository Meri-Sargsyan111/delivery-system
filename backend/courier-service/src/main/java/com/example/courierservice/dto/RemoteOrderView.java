package com.example.courierservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Trimmed local view of order-service's GET /orders/{id}/status projection, used to
 * read the current status and ownership ids over REST. Deliberately not shared as a
 * common module - each service keeps its own copy per the project's microservice
 * boundaries. Ignores unknown properties for forward-compatibility.
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
