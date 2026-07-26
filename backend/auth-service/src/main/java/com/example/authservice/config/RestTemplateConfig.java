package com.example.authservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Shared by NotificationServiceClient. Without an explicit timeout, the JDK's default
     * HTTP request factory never times out at all - a slow/unresponsive downstream call
     * then blocks the request thread indefinitely, well past api-gateway's own 15s
     * response-timeout. Kept well under that budget so a genuinely slow/unreachable
     * dependency fails fast - see NotificationServiceClient's existing catch blocks, which
     * only degrade gracefully in a useful timeframe once this actually bounds the call.
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