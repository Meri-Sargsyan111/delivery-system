package com.example.paymentservice.provider.dto;

import com.example.paymentservice.entity.PaymentStatus;

/** mappedStatus is null for event types this adapter doesn't recognize/care about - the caller should ignore those, not fail. */
public record WebhookEvent(
        String providerEventId,
        String providerReference,
        PaymentStatus mappedStatus,
        String eventType
) {
}
