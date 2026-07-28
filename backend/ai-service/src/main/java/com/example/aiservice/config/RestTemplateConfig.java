package com.example.aiservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Used by GeocodingClient and RoutingClient only. Without an explicit timeout a slow
     * public Nominatim/OSRM response blocks the request thread indefinitely; both clients
     * also retry internally with their own short backoff, so 30s is a safe outer bound
     * for the whole retry sequence.
     */
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${http-client.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${http-client.read-timeout-ms:30000}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    /**
     * OllamaClient gets its own RestTemplate with a deliberately shorter read timeout
     * than the general-purpose one above. /api/ai/chat and /ai/estimate are both proxied
     * through api-gateway, whose spring.cloud.gateway.httpclient.response-timeout is 15s
     * (see api-gateway's application.yml) - if this call were allowed to run as long as
     * 30s, ai-service would still be waiting on Ollama well after the gateway has already
     * given up and returned a bare 504 to the browser, so the eventual real response (or
     * the graceful 503 OllamaClient would otherwise return on a genuine failure) is
     * silently discarded and the caller never learns why. Keeping this timeout safely
     * under the gateway's means ai-service always loses that race on purpose: it fails
     * fast enough to hand back its own informative error before the gateway's blind
     * cutoff fires. See also OllamaWarmupRunner and the request-level keep_alive in
     * OllamaGenerateRequest, which address the actual cause of slow calls (a cold model
     * load) rather than just widening the window tolerated here.
     */
    @Bean
    public RestTemplate ollamaRestTemplate(
            RestTemplateBuilder builder,
            @Value("${http-client.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${ollama.read-timeout-ms:11000}") long ollamaReadTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(ollamaReadTimeoutMs))
                .build();
    }
}
