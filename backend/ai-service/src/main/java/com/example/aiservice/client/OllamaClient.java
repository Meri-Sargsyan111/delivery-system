package com.example.aiservice.client;

import com.example.aiservice.client.dto.OllamaGenerateRequest;
import com.example.aiservice.client.dto.OllamaGenerateResponse;
import com.example.aiservice.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

/**
 * Synchronous call to a local Ollama instance's /api/generate endpoint. No API key is
 * involved - Ollama runs locally and is reached over plain HTTP (see application.yml's
 * ollama.base-url).
 */
@Slf4j
@Component
public class OllamaClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;

    public OllamaClient(RestTemplate restTemplate,
                         @Value("${ollama.base-url}") String baseUrl,
                         @Value("${ollama.model}") String model) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public String generate(String systemPrompt, String userMessage) {
        OllamaGenerateRequest request = new OllamaGenerateRequest(model, systemPrompt, userMessage, false);
        OllamaGenerateResponse response = call(request);
        return extractReply(response);
    }

    private OllamaGenerateResponse call(OllamaGenerateRequest request) {
        try {
            return restTemplate.postForObject(baseUrl, request, OllamaGenerateResponse.class);
        } catch (HttpStatusCodeException ex) {
            log.error("Ollama API call failed: {}", ex.getResponseBodyAsString());
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI is temporarily unavailable");
        } catch (ResourceAccessException ex) {
            log.error("Ollama API unreachable at {}", baseUrl, ex);
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "AI is temporarily unavailable");
        }
    }

    private String extractReply(OllamaGenerateResponse response) {
        if (response == null || !StringUtils.hasText(response.response())) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI is temporarily unavailable");
        }
        return response.response();
    }
}
