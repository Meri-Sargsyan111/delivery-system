package com.example.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row in the current user's conversation list. lastMessage/lastMessageAt are null for a
 * conversation that has an assigned courier but no messages sent yet - see
 * ChatServiceImpl.getConversations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryResponse {

    private Long orderId;
    private String customerName;
    private String courierName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
}
