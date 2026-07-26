package com.example.chatservice.dto;

/**
 * The only client-supplied field for a read-marker ping - sender identity is server-derived,
 * same as SendMessageRequest. Purely ephemeral: never persisted, only relayed to the other
 * participant via /topic/chat/order/{orderId}/read.
 */
public record ReadMarkerRequest(Long lastReadMessageId) {}
