package com.example.chatservice.dto;

/**
 * Pushed to /topic/chat/unread/{userId} whenever a new message arrives for, or gets marked
 * read by, the target user (see ChatServiceImpl.pushUnreadCountUpdate) - carries both the
 * per-conversation and grand-total counts in one push so the frontend never needs a
 * follow-up REST fetch to keep a global badge and a per-conversation badge in sync.
 */
public record UnreadCountUpdate(Long orderId, long conversationUnreadCount, long totalUnreadCount) {}
