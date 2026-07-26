package com.example.aiservice.controller;

import com.example.aiservice.dto.ChatRequest;
import com.example.aiservice.dto.ChatResponse;
import com.example.aiservice.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("POST /api/ai/chat");
        return ResponseEntity.ok(new ChatResponse(aiChatService.chat(request.getMessage())));
    }
}
