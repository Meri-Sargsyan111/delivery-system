package com.example.courierservice.service;

import com.example.courierservice.dto.RateOrderRequest;
import com.example.courierservice.dto.RatingResponse;

/**
 * Manages courier ratings submitted for completed deliveries.
 */
public interface CourierRatingService {

    /**
     * Rates the courier who delivered the given order. The order must have a
     * recorded assignment and must currently be {@code DELIVERED}, and must not
     * already have been rated.
     *
     * @param orderId the delivered order being rated
     * @param request the rating payload (value 1-5)
     * @return the persisted rating
     * @throws com.example.courierservice.exception.EntityNotFoundException if no assignment exists for the order
     * @throws com.example.courierservice.exception.InvalidOrderStateException if the order is not {@code DELIVERED}
     * @throws com.example.courierservice.exception.OrderAlreadyRatedException if the order has already been rated
     */
    RatingResponse rateOrder(Long orderId, RateOrderRequest request);
}