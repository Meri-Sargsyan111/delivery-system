package com.example.courierservice.service;

/**
 * Service interface for managing courier delivery operations.
 *
 * <p>Provides methods to control the lifecycle of a delivery, from initiating
 * the pickup to marking the order as successfully delivered to the customer.
 */
public interface CourierService {

    /**
     * Initiates the delivery process for the specified order.
     *
     * <p>Only valid for an order currently {@code ASSIGNED} to a courier; the current
     * status is verified synchronously against order-service before proceeding.
     *
     * @param orderId the unique identifier of the order to be delivered
     * @return a confirmation message describing the result of the operation
     * @throws com.example.courierservice.exception.InvalidOrderStateException if the order is not {@code ASSIGNED}
     */
    String startDelivery(Long orderId);

    /**
     * Marks the specified order as successfully delivered and frees the assigned
     * courier (transitions it back to {@code AVAILABLE}).
     *
     * @param orderId the unique identifier of the order that was delivered
     * @return a confirmation message describing the result of the operation
     * @throws com.example.courierservice.exception.InvalidOrderStateException if the order is not deliverable from its current status
     */
    String markAsDelivered(Long orderId);
}
