package com.example.paymentservice.provider;

/**
 * Thrown by an adapter whose real integration isn't wired in yet (e.g. Rocket Line,
 * pending real API documentation) - a deliberate, honest failure rather than a fabricated
 * response. Also thrown by PaymentProviderFactory when no adapter is registered at all
 * for a given provider.
 */
public class PaymentProviderNotConfiguredException extends RuntimeException {

    public PaymentProviderNotConfiguredException(String message) {
        super(message);
    }
}
