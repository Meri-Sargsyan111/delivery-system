package com.example.chatservice.dto;

import java.util.UUID;

/** Broadcast to /topic/chat/order/{orderId}/read - purely ephemeral, never persisted. */
public record ReadMarkerBroadcast(UUID userId, Long lastReadMessageId) {}
