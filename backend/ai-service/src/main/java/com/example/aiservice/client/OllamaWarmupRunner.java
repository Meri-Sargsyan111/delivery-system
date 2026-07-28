package com.example.aiservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fires one throwaway generate call against Ollama right after startup so the model is
 * already resident in memory (see OllamaGenerateRequest's keep_alive=-1) before the
 * first real user hits /api/ai/chat or /ai/estimate. Without this, whichever request
 * happens to be first pays Ollama's full model-load cost (several seconds, measured
 * ~4.7s for llama3.2:1b) on top of generation time - exactly the latency that was
 * exceeding api-gateway's 15s response-timeout and surfacing as a bare 504.
 * <p>
 * Runs on a plain daemon thread, not the Spring startup thread: Ollama being slow or
 * briefly unavailable at boot must never delay or fail ai-service's own startup, since
 * every other ai-service endpoint (and its health check) is unrelated to Ollama.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaWarmupRunner {

    private final OllamaClient ollamaClient;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread warmupThread = new Thread(this::attemptWarmup, "ollama-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    private void attemptWarmup() {
        long start = System.currentTimeMillis();
        try {
            ollamaClient.generate("Reply with only the word OK.", "OK");
            log.info("Ollama warm-up complete in {}ms - model is now resident in memory", System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.warn("Ollama warm-up failed after {}ms - the first real /api/ai/chat or /ai/estimate "
                    + "request will pay the model-load cost instead. Ollama may not be running yet.",
                    System.currentTimeMillis() - start, ex);
        }
    }
}