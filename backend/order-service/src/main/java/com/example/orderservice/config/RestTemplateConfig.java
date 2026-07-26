package com.example.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Shared by AuthServiceClient and CourierServiceClient. Without an explicit timeout,
     * the JDK's default HTTP request factory never times out at all - a slow/unresponsive
     * downstream call (e.g. auth-service under load) then blocks the request thread
     * indefinitely, well past api-gateway's own 15s response-timeout, which is what
     * actually surfaces to the customer as "Response took longer than timeout: PT15S".
     * Both timeouts are kept well under that 15s budget so a genuinely slow/unreachable
     * dependency fails fast enough for the caller to still return a clean error in time.
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