package com.example.paymentservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dedupe table for webhook replay/duplicate-delivery protection - providers (Stripe
 * included) explicitly document at-least-once webhook delivery, so the same event id can
 * arrive more than once. Insert-or-skip on (provider, providerEventId) before processing.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "processed_webhook_events", indexes = {
        @Index(name = "idx_processed_webhook_events_provider_event",
                columnList = "provider,providerEventId", unique = true)
})
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;
    private String providerEventId;
    private LocalDateTime receivedAt;
}
