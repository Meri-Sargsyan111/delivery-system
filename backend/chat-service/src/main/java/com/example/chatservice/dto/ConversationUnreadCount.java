package com.example.chatservice.dto;

/** One conversation's unread count for the current user - see ChatMessageRepository.countUnreadGroupedByOrderId. */
public record ConversationUnreadCount(Long orderId, Long unreadCount) {}
