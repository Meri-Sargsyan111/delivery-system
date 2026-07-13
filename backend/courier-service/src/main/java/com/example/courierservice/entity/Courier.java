package com.example.courierservice.entity;

import com.example.courierservice.courier.CourierStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "couriers")
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private CourierStatus status;

    private String photoUrl;

    /**
     * The auth-service user id (JWT "sub") of the ROLE_COURIER account that operates
     * this courier record. Admin-settable only (see CreateCourierRequest) - never
     * matched by display name. Null on legacy/unlinked courier records, which remain
     * admin-manageable only until an admin links them to a real courier account.
     */
    private UUID userId;
}