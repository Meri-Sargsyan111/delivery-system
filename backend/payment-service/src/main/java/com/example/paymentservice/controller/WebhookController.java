package com.example.paymentservice.controller;

import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public entry point for provider webhooks (see SecurityConfig - permitAll at the HTTP
 * layer, real authentication is the SDK-verified signature inside PaymentService).
 * Providers call this directly with no JWT - Stripe's own signature scheme
 * (Stripe-Signature header, verified via the official SDK's Webhook.constructEvent) is
 * the actual authentication here, not a bearer token. The raw request body is read as a
 * plain String and passed through byte-for-byte to the signature verifier - Stripe's
 * signature is computed over the exact bytes sent, so any re-serialization here (e.g.
 * binding to a typed object first) would break verification.
 */
@Slf4j
@RestController
@RequestMapping("/payments/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";
    private static final String GENERIC_SIGNATURE_HEADER = "X-Webhook-Signature";

    private final PaymentService paymentService;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String provider,
            @RequestBody String rawPayload,
            @RequestHeader(value = STRIPE_SIGNATURE_HEADER, required = false) String stripeSignature,
            @RequestHeader(value = GENERIC_SIGNATURE_HEADER, required = false) String genericSignature) {

        String signature = stripeSignature != null ? stripeSignature : genericSignature;
        log.info("POST /payments/webhook/{}", provider);
        paymentService.handleWebhook(provider, rawPayload, signature);
        return ResponseEntity.ok().build();
    }
}
