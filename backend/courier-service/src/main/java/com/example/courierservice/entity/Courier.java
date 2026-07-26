package com.example.courierservice.entity;

import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.courier.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    /**
     * Nullable - existing rows predate this column. CourierAssignmentServiceImpl
     * defaults it to CAR at creation time when not supplied; a null value read back
     * here (pre-existing row) is treated as CAR by the same default at the response
     * mapping layer rather than backfilled.
     */
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    /**
     * Incremented once per order this courier finishes (see CourierServiceImpl.markAsDelivered).
     * Exposed on the live-tracking contact card - see CourierContactCardResponse.
     */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int completedDeliveries;

    /** Pre-vehicleType constructor, kept so existing call sites/tests need no changes. */
    public Courier(Long id, String name, CourierStatus status, String photoUrl, UUID userId) {
        this(id, name, status, photoUrl, userId, null);
    }

    /** Pre-completedDeliveries constructor, kept so existing call sites/tests need no changes. */
    public Courier(Long id, String name, CourierStatus status, String photoUrl, UUID userId, VehicleType vehicleType) {
        this(id, name, status, photoUrl, userId, vehicleType, 0);
    }
}