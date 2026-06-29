package com.example.courierservice.service;

/**
 * Service interface for managing delivery status transitions.
 *
 * <p>Handles terminal state changes in the delivery lifecycle, such as
 * completing a delivery and propagating the resulting status updates
 * to dependent services (e.g., order service, notification service).
 */
public interface DeliveryStatusService {

    /**
     * Completes the delivery for the specified order and updates its status accordingly.
     *
     * <p>This method finalizes the delivery workflow: it updates the order status,
     * publishes relevant events to downstream consumers, and triggers any
     * post-completion side effects such as customer notifications.
     *
     * @param orderId the unique identifier of the order whose delivery is being completed
     */
    void completeDelivery(Long orderId);

}