package com.example.authservice.exception;

public class InvalidRegistrationRoleException extends RuntimeException {

    public InvalidRegistrationRoleException(String message) {
        super(message);
    }
}