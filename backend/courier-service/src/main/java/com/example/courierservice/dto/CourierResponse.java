package com.example.courierservice.dto;

import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.courier.VehicleType;
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
    private VehicleType vehicleType;

    /**
     * Cross-service, from auth-service (see AuthServiceClient) - null when the courier has
     * no linked account or auth-service is unreachable, matching the frontend's Courier.phone
     * being optional/nullable (courier.service.ts).
     */
    private String phone;

    /** Always present (defaults to 0) - see Courier.completedDeliveries. */
    private Integer completedDeliveries;

    /** Pre-vehicleType constructor, kept so existing call sites/tests need no changes. */
    public CourierResponse(Long id, String name, CourierStatus status, Double averageRating,
                            Integer ratingCount, String photoUrl) {
        this(id, name, status, averageRating, ratingCount, photoUrl, null, null, null);
    }

    /** Pre-phone/completedDeliveries constructor, kept so existing call sites/tests need no changes. */
    public CourierResponse(Long id, String name, CourierStatus status, Double averageRating,
                            Integer ratingCount, String photoUrl, VehicleType vehicleType) {
        this(id, name, status, averageRating, ratingCount, photoUrl, vehicleType, null, null);
    }
}