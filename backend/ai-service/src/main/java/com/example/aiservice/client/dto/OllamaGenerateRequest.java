package com.example.aiservice.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code keepAlive} ("keep_alive" on the wire, Ollama's naming) tells Ollama how long to
 * keep this model resident in memory after this call. Ollama's own default is 5 minutes;
 * any request arriving after that window pays a multi-second model-load cost on top of
 * actual generation, which is what was blowing past the API Gateway's response-timeout
 * and surfacing as a 504 with no useful information. Sending "-1" here keeps the model
 * loaded indefinitely once it's warm (see OllamaWarmupRunner, which loads it once at
 * startup so even the very first real request never pays that cost either). Must be a
 * Go duration string with an explicit unit - Ollama rejects a bare "-1" (no unit) with
 * a 400; "-1s" is the documented way to say "never unload".
 */
public record OllamaGenerateRequest(
        String model, String system, String prompt, boolean stream, @JsonProperty("keep_alive") String keepAlive) {

    public OllamaGenerateRequest(String model, String system, String prompt, boolean stream) {
        this(model, system, prompt, stream, "-1s");
    }
}
