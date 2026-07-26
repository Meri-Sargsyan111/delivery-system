package com.example.authservice.exception;

public class InvalidPreferenceException extends RuntimeException {

    public InvalidPreferenceException(String message) {
        super(message);
    }
}