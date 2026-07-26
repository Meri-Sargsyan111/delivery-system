package com.example.aiservice.client.dto;

public record OllamaGenerateRequest(String model, String system, String prompt, boolean stream) {
}
