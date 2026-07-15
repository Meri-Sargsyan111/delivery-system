package com.example.chatservice.dto;

/**
 * The only field a client ever supplies - sender identity/role/timestamp are server-derived.
 * Not validated via Bean Validation (the STOMP {@code @MessageMapping} that consumes this
 * has no {@code @Valid}) - blank/length checks are enforced in ChatServiceImpl.sendMessage.
 */
public record SendMessageRequest(String content) {}