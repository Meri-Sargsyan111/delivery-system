package com.example.courierservice.service.impl;

import com.example.courierservice.client.OrderServiceClient;
import com.example.courierservice.dto.AssignmentResponse;
import com.example.courierservice.dto.RateOrderRequest;
import com.example.courierservice.dto.RatingResponse;
import com.example.courierservice.dto.RemoteOrderView;
import com.example.courierservice.entity.CourierRating;
import com.example.courierservice.exception.InvalidOrderStateException;
import com.example.courierservice.exception.OrderAlreadyRatedException;
import com.example.courierservice.repository.CourierRatingRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.CourierAssignmentService;
import com.example.courierservice.service.CourierRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierRatingServiceImpl implements CourierRatingService {

    private final CourierRatingRepository courierRatingRepository;
    private final CourierAssignmentService courierAssignmentService;
    private final OrderServiceClient orderServiceClient;
    private final CurrentUser currentUser;

    @Override
    public RatingResponse rateOrder(Long orderId, RateOrderRequest request) {

        AssignmentResponse assignment = courierAssignmentService.getAssignment(orderId);

        RemoteOrderView order = orderServiceClient.getOrder(orderId);

        if (!currentUser.isAdmin() && isNotOwningCustomer(order)) {
            throw new AccessDeniedException("Not authorized to rate order " + orderId);
        }

        if (!"DELIVERED".equals(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " cannot be rated: current status is " + order.getStatus());
        }

        if (courierRatingRepository.existsByOrderId(orderId)) {
            throw new OrderAlreadyRatedException("Order " + orderId + " has already been rated");
        }

        CourierRating rating = new CourierRating(null, orderId, assignment.getCourierId(),
                request.getValue(), LocalDateTime.now());

        try {
            courierRatingRepository.save(rating);
        } catch (DataIntegrityViolationException ex) {

            throw new OrderAlreadyRatedException("Order " + orderId + " has already been rated");
        }

        log.info("Order {} rated {} for courier {}", orderId, request.getValue(), assignment.getCourierId());

        return new RatingResponse(rating.getId(), rating.getOrderId(), rating.getCourierId(),
                rating.getValue(), rating.getRatedAt());
    }

    private boolean isNotOwningCustomer(RemoteOrderView order) {
        return order.getCustomerUserId() == null || !currentUser.getUserId().equals(order.getCustomerUserId());
    }
}