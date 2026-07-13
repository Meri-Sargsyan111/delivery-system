package com.example.chatservice.exception;

/** Thrown when trying to send a message on an order that has reached a terminal state. */
public class ChatSendingDisabledException extends RuntimeException {

    public ChatSendingDisabledException(String message) {
        super(message);
    }
}