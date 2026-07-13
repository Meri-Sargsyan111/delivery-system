package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.DeliveryOrderResponse;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.OrderStatusView;
import com.example.orderservice.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for managing delivery orders throughout their lifecycle.
 *
 * <p>Covers order creation, retrieval, search, and all status transitions
 * (assigned, delivered, cancelled), with Kafka event publishing on creation.
 */
public interface OrderService {

    /**
     * Creates a new delivery order from the given request, persists it with
     * status {@code CREATED}, and publishes an {@code OrderCreatedEvent} to Kafka.
     *
     * @param request the order creation payload containing customer and address details
     * @return an {@link OrderResponse} with the new order's ID and a confirmation message
     */
    OrderResponse createOrder(CreateOrderRequest request);

    /**
     * Returns a paginated list of all delivery orders.
     *
     * @param pageable pagination and sorting parameters
     * @return a {@link Page} of {@link DeliveryOrderResponse}
     */
    Page<DeliveryOrderResponse> getOrders(Pageable pageable);

    /**
     * Retrieves a single order by its ID.
     *
     * @param id the unique identifier of the order
     * @return the matching {@link DeliveryOrderResponse}
     * @throws RuntimeException if no order with the given ID exists
     */
    DeliveryOrderResponse getOrderById(Long id);

    /**
     * Retrieves the status projection for a single order by its ID with no
     * ownership/role check. Used only by the public GET /orders/{id}/status internal
     * projection endpoint - deliberately excludes customerName/addresses/customerPhone,
     * see {@link OrderStatusView}.
     *
     * @param id the unique identifier of the order
     * @return the {@link OrderStatusView} projection for that order
     */
    OrderStatusView getOrderByIdInternal(Long id);

    /**
     * Returns a paginated list of orders filtered by optional customer name and status.
     *
     * @param customerName partial or full customer name to filter by (may be {@code null})
     * @param status       order status to filter by (may be {@code null})
     * @param pageable     pagination and sorting parameters
     * @return a {@link Page} of matching {@link DeliveryOrderResponse}
     */
    Page<DeliveryOrderResponse> searchOrders(String customerName, OrderStatus status, Pageable pageable);

    /**
     * Reserves the given courier (via courier-service) and, on success, transitions
     * the order to {@code ASSIGNED} status and persists the assigned courier's ID.
     *
     * @param id the unique identifier of the order to assign
     * @param courierId the unique identifier of the courier to assign
     * @return an {@link OrderResponse} confirming the assignment
     * @throws com.example.orderservice.exception.InvalidOrderStateException if the order is not {@code CREATED}
     * @throws com.example.orderservice.exception.CourierAssignmentException if courier-service rejects the reservation
     */
    OrderResponse assignOrder(Long id, Long courierId);

    /**
     * Transitions the order to {@code IN_PROGRESS} status and persists the change.
     * Called when courier-service reports that delivery has started.
     *
     * @param id the unique identifier of the order
     */
    void startProgress(Long id);

    /**
     * Transitions the order to {@code DELIVERED} status and persists the change.
     *
     * @param id the unique identifier of the order to mark as delivered
     * @return an {@link OrderResponse} confirming the delivery
     */
    OrderResponse deliverOrder(Long id);

    /**
     * Transitions the order to {@code CANCELLED} status and persists the change.
     *
     * @param id the unique identifier of the order to cancel
     * @return an {@link OrderResponse} confirming the cancellation
     */
    OrderResponse cancelOrder(Long id);
}
