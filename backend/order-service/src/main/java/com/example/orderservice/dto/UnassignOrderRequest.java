package com.example.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of PUT /orders/internal/{id}/unassign - called by courier-service when a courier
 * rejects an assignment, or its offer-timeout sweep gives up on an unanswered one (see
 * InternalServiceTokenFilter for how this endpoint is protected). courierId guards against
 * unassigning an order that's since moved on to a different courier.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnassignOrderRequest {

    @NotNull
    private Long courierId;
}