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
     * <p>Assigns the order to an available courier and transitions its status
     * to indicate that delivery is in progress.
     *
     * @param orderId the unique identifier of the order to be delivered
     * @return a confirmation message describing the result of the operation
     */
    String startDelivery(Long orderId);

    /**
     * Marks the specified order as successfully delivered.
     *
     * <p>Updates the order status to delivered and triggers any downstream
     * notifications or post-delivery workflows.
     *
     * @param orderId the unique identifier of the order that was delivered
     * @return a confirmation message describing the result of the operation
     */
    String markAsDelivered(Long orderId);
}
