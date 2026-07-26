package com.example.notificationservice.dto;

import com.example.notificationservice.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String message,
        NotificationType type,
        boolean playSound,
        LocalDateTime createdAt
) {}
