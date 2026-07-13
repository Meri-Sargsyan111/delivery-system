package com.example.chatservice.dto;

/** Sent privately (via /user/queue/chat/errors) to the sender when their SEND is rejected. */
public record ChatErrorResponse(String message) {}