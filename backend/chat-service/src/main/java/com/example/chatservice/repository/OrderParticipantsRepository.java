package com.example.chatservice.repository;

import com.example.chatservice.entity.OrderParticipants;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderParticipantsRepository extends JpaRepository<OrderParticipants, Long> {

    /** Every order this user participates in, as either the customer or the assigned courier. */
    List<OrderParticipants> findByCustomerUserIdOrCourierUserId(UUID customerUserId, UUID courierUserId);
}