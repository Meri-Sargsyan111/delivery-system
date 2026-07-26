package com.example.paymentservice.entity;

/**
 * What the customer actually selected at checkout. VISA/MASTERCARD are card brands, not
 * separate processors - both route through Stripe (see PaymentProvider). ROCKET_LINE is
 * both the method and its own provider.
 */
public enum PaymentMethodType {
    VISA(PaymentProvider.STRIPE),
    MASTERCARD(PaymentProvider.STRIPE),
    ROCKET_LINE(PaymentProvider.ROCKET_LINE);

    private final PaymentProvider provider;

    PaymentMethodType(PaymentProvider provider) {
        this.provider = provider;
    }

    public PaymentProvider getProvider() {
        return provider;
    }
}
