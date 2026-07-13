package com.example.courierservice.dto;

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
}