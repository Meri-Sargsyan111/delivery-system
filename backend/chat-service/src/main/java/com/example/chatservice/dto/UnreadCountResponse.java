package com.example.chatservice.dto;

/** Response for GET /chat/unread-count - total unread messages addressed to the caller, across every conversation. */
public record UnreadCountResponse(long totalUnread) {}
