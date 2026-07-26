package com.example.paymentservice.provider.dto;

/** clientSecret is what a frontend would use with the provider's own JS SDK (e.g. Stripe.js/Elements) to complete the payment. */
public record ProviderPaymentResult(String providerReference, String clientSecret, String rawResponseJson) {
}
