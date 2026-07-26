package com.example.paymentservice.exception;

import org.springframework.http.HttpStatus;

/** Thrown by AiServiceClient.claimEstimate - the estimate didn't exist, was already claimed, expired, or ai-service was unreachable. */
public class EstimateClaimException extends RuntimeException {

    private final HttpStatus status;

    public EstimateClaimException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
