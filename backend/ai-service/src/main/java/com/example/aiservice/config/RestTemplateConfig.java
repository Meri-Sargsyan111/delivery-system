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
     * Shared by OllamaClient, GeocodingClient, and RoutingClient. Without an explicit
     * timeout a hung Ollama instance or a slow public Nominatim/OSRM response blocks the
     * request thread indefinitely. The read timeout is generous because local LLM
     * generation on CPU can legitimately take several seconds.
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
}
