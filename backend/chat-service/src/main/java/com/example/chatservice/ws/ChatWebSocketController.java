package com.example.chatservice.ws;

import com.example.chatservice.dto.ChatErrorResponse;
import com.example.chatservice.dto.SendMessageRequest;
import com.example.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * Client SEND destination: /app/chat/{orderId} (application-prefixed, per WebSocketConfig).
 * Server broadcast destination: /topic/chat/order/{orderId} - only participants who
 * successfully authorized a SUBSCRIBE to that exact topic (see ChatChannelInterceptor)
 * ever receive it.
 *
 * Identity is resolved via ChatChannelInterceptor's session-id-keyed cache (see
 * ChatServiceImpl.sendFromSession), not the Principal method-argument Spring would
 * normally inject here - see ChatChannelInterceptor's javadoc for why (accessor.getUser()
 * does not reliably propagate from CONNECT to later frames in this setup).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;

    @MessageMapping("/chat/{orderId}")
    public void send(@DestinationVariable Long orderId, @Payload SendMessageRequest request,
                      @Header("simpSessionId") String sessionId) {
        chatService.sendFromSession(orderId, sessionId, request.content());
    }

    @MessageExceptionHandler
    @SendToUser("/queue/chat/errors")
    public ChatErrorResponse handleException(Exception ex) {
        log.warn("Chat WebSocket send rejected: {}", ex.getMessage());
        return new ChatErrorResponse(ex.getMessage());
    }
}