package com.example.chatservice.mapper;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.entity.ChatMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageMapper {

    public ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getOrderId(),
                message.getSenderUserId(),
                message.getSenderRole(),
                message.getContent(),
                message.getSentAt()
        );
    }
}