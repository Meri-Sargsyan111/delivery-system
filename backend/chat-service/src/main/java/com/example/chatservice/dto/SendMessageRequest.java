package com.example.chatservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The only field a client ever supplies - sender identity/role/timestamp are server-derived. */
public record SendMessageRequest(
        @NotBlank(message = "message content must not be blank")
        @Size(max = 2000, message = "message content exceeds the maximum length")
        String content
) {}