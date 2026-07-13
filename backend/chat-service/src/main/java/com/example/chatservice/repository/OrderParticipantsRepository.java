package com.example.chatservice.repository;

import com.example.chatservice.entity.OrderParticipants;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderParticipantsRepository extends JpaRepository<OrderParticipants, Long> {
}