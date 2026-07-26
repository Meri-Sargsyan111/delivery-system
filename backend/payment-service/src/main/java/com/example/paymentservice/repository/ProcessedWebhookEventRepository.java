package com.example.paymentservice.repository;

import com.example.paymentservice.entity.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, Long> {

    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
