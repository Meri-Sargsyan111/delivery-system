package com.example.authservice.exception;

public class InvalidAvatarFileException extends RuntimeException {

    public InvalidAvatarFileException(String message) {
        super(message);
    }
}