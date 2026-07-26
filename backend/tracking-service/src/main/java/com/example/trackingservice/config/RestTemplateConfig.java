package com.example.trackingservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Shared by OrderServiceClient (TrackingAccessGuard's fallback ownership check) and
     * GeocodingClient/RoutingClient (Nominatim/OSRM). Without an explicit timeout, the
     * JDK's default HTTP request factory never times out at all - a slow/unresponsive
     * downstream call then blocks the calling thread indefinitely. For OrderServiceClient
     * that thread is a customer-facing request, well past api-gateway's own 15s
     * response-timeout; for the geocoding/routing clients it's a Kafka consumer thread.
     * Kept well under the gateway's budget so a genuinely slow/unreachable dependency
     * fails fast either way.
     */
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${http-client.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${http-client.read-timeout-ms:5000}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}