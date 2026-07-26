package com.example.authservice.exception;

public class NotificationServiceUnavailableException extends RuntimeException {

    public NotificationServiceUnavailableException(String message) {
        super(message);
    }
}
