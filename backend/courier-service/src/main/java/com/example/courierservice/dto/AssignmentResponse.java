package com.example.courierservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {

    private Long orderId;
    private Long courierId;
    private String courierName;
    private LocalDateTime assignedAt;
}