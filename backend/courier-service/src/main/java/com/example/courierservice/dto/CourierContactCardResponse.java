package com.example.courierservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Everything the live-tracking page needs to render a courier's contact card. phoneNumber
 * is null when the courier has no linked auth-service account or auth-service is
 * unreachable (see AuthServiceClient) - the endpoint still returns 200 with the rest of
 * the card populated rather than failing outright. rating is null when the courier has no
 * ratings yet, same as CourierResponse.averageRating.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierContactCardResponse {

    private String fullName;
    private String phoneNumber;
    private Double rating;
    private int completedDeliveries;
}