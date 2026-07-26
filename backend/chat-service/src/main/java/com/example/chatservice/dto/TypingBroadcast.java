package com.example.chatservice.dto;

import java.util.UUID;

/** Broadcast to /topic/chat/order/{orderId}/typing - purely ephemeral, never persisted. */
public record TypingBroadcast(UUID userId) {}
