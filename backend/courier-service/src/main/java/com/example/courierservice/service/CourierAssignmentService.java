package com.example.courierservice.service;

import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.dto.AssignmentResponse;
import com.example.courierservice.dto.CourierResponse;
import com.example.courierservice.dto.CreateCourierRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Manages the courier roster and the assignment relationship between
 * couriers and orders (dispatcher-facing operations).
 */
public interface CourierAssignmentService {

    /**
     * Lists couriers, optionally filtered by status. Paginated - the roster grows
     * unbounded as couriers are onboarded, so admin/general listing must not return
     * everything at once.
     *
     * @param status the status to filter by, or {@code null} for all couriers
     * @param pageable pagination and sorting parameters
     * @return a page of matching couriers
     */
    Page<CourierResponse> listCouriers(CourierStatus status, Pageable pageable);

    /**
     * Returns all couriers currently {@code AVAILABLE}.
     */
    List<CourierResponse> getAvailableCouriers();

    /**
     * Returns the courier profile linked to the authenticated COURIER caller.
     *
     * @throws com.example.courierservice.exception.EntityNotFoundException if the caller has no linked courier record
     */
    CourierResponse getMyCourier();

    /**
     * Returns the assignment recorded for the given order.
     *
     * @throws com.example.courierservice.exception.EntityNotFoundException if no assignment exists for the order
     */
    AssignmentResponse getAssignment(Long orderId);

    /**
     * Reserves the given courier for the given order: validates the courier is
     * {@code AVAILABLE}, marks it {@code BUSY}, and persists the assignment.
     *
     * @throws com.example.courierservice.exception.EntityNotFoundException if the courier does not exist
     * @throws com.example.courierservice.exception.CourierNotAvailableException if the courier is {@code BUSY} or {@code OFFLINE}
     * @return the reserved courier's linked user id, or {@code null} for a legacy/unlinked courier record
     */
    UUID reserveCourier(Long courierId, Long orderId);

    /**
     * Frees the courier assigned to the given order (sets it back to {@code AVAILABLE}).
     * A no-op if no assignment is found for the order.
     */
    void releaseCourierForOrder(Long orderId);

    /**
     * Creates a new courier with status {@code AVAILABLE}. Status is never accepted
     * from the caller - it is always assigned server-side.
     *
     * @param request the courier creation payload
     * @return the newly created courier
     */
    CourierResponse createCourier(CreateCourierRequest request);

    /**
     * Manually changes a courier's status. Only {@code AVAILABLE <-> OFFLINE} is
     * allowed: {@code BUSY} is system-managed and can never be set manually, and a
     * courier currently {@code BUSY} (on an active delivery) can never be manually
     * changed to any other status.
     *
     * @param courierId the courier to change
     * @param targetStatus the requested new status
     * @return the updated courier
     * @throws com.example.courierservice.exception.EntityNotFoundException if the courier does not exist
     * @throws IllegalArgumentException if {@code targetStatus} is {@code BUSY}
     * @throws com.example.courierservice.exception.CourierNotAvailableException if the courier is currently {@code BUSY}
     */
    CourierResponse changeStatus(Long courierId, CourierStatus targetStatus);

    /**
     * Creates the Courier profile for a newly registered ROLE_COURIER user, linked
     * via {@code userId}, with status {@code AVAILABLE} - matching the status
     * {@link #createCourier} always assigns. A no-op if a courier is already linked
     * to this userId (idempotent under Kafka redelivery).
     *
     * @param userId the auth-service user id (JWT "sub") of the new courier account
     * @param name the courier's display name
     */
    void registerCourierAccount(UUID userId, String name);
}