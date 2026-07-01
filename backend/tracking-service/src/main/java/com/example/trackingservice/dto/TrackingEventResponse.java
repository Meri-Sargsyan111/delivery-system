package com.example.trackingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackingEventResponse {

    private Long id;
    private Long orderId;
    private String courierName;
    private String status;
    private LocalDateTime eventTime;
}
