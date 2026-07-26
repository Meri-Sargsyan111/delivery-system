package com.example.courierservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierLocation {

    private Long orderId;
    private double latitude;
    private double longitude;

    /** Degrees, 0-360, direction of travel. 0 when unknown (e.g. a stationary courier). */
    private double bearing;

    /** 0 when unknown. */
    private double speedKmh;
}