package com.example.aiservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Projection of Ollama's /api/generate response - only what's needed to read back the
 * assistant's reply. {@code ignoreUnknown} because the real payload also carries
 * model/created_at/done/context/durations/etc. this service has no use for.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaGenerateResponse(String response) {
}
