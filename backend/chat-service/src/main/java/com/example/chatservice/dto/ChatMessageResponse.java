package com.example.chatservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        Long id,
        Long orderId,
        UUID senderUserId,
        String senderRole,
        String content,
        LocalDateTime sentAt
) {}