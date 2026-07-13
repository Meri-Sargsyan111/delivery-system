package com.example.courierservice.exception;

public class CourierNotAvailableException extends RuntimeException {

    public CourierNotAvailableException(String message) {
        super(message);
    }
}
