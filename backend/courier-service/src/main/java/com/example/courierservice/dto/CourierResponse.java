package com.example.courierservice.dto;

import com.example.courierservice.courier.CourierStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierResponse {

    private Long id;
    private String name;
    private CourierStatus status;
    private Double averageRating;
    private Integer ratingCount;
    private String photoUrl;
}