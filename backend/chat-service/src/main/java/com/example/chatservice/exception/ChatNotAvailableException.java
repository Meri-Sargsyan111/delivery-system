package com.example.chatservice.exception;

/** Thrown when an order exists but chat isn't available yet - no courier assigned. */
public class ChatNotAvailableException extends RuntimeException {

    public ChatNotAvailableException(String message) {
        super(message);
    }
}