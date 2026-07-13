package com.example.courierservice.exception;

public class OrderAlreadyRatedException extends RuntimeException {

    public OrderAlreadyRatedException(String message) {
        super(message);
    }
}