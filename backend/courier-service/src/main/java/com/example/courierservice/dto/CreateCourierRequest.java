package com.example.courierservice.dto;

import com.example.courierservice.courier.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCourierRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    @Pattern(regexp = "^https?://\\S+$", message = "photoUrl must be a valid http:// or https:// URL")
    private String photoUrl;

    /** Optional: links this courier record to a ROLE_COURIER user account. Admin-only field. */
    private UUID userId;

    /** Optional - defaults to CAR when omitted (see CourierAssignmentServiceImpl). */
    private VehicleType vehicleType;

    /** Pre-vehicleType constructor, kept so existing call sites/tests need no changes. */
    public CreateCourierRequest(String name, String photoUrl, UUID userId) {
        this(name, photoUrl, userId, null);
    }
}