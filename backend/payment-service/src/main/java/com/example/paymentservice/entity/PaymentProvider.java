package com.example.paymentservice.entity;

/**
 * The actual payment processor a payment was routed through - distinct from
 * PaymentMethodType (what the customer picked). Adding a new provider is: a new value
 * here, a new PaymentProviderAdapter implementation, and a mapping entry in
 * PaymentMethodType.getProvider() - no change to PaymentServiceImpl's business logic.
 */
public enum PaymentProvider {
    STRIPE,
    ROCKET_LINE
}
