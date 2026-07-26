package com.example.chatservice.controller;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ConversationSummaryResponse;
import com.example.chatservice.dto.ConversationUnreadCount;
import com.example.chatservice.dto.UnreadCountResponse;
import com.example.chatservice.security.CurrentUser;
import com.example.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final CurrentUser currentUser;

    /** Conversation list for the caller: order id, customer/courier names, last message, and unread count. */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummaryResponse>> getConversations() {
        return ResponseEntity.ok(chatService.getConversations(currentUser.getUserId()));
    }

    @GetMapping("/orders/{orderId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getHistory(
            @PathVariable Long orderId,
            @PageableDefault(size = 50, sort = "sentAt", direction = Sort.Direction.ASC) Pageable pageable) {

        log.info("GET /chat/orders/{}/messages", orderId);
        return ResponseEntity.ok(chatService.getHistory(orderId, pageable));
    }

    /** Grand total unread count for the caller, across every conversation they participate in. */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        long total = chatService.getUnreadCount(currentUser.getUserId());
        return ResponseEntity.ok(new UnreadCountResponse(total));
    }

    /** One entry per conversation (orderId) that currently has unread messages for the caller. */
    @GetMapping("/unread-counts")
    public ResponseEntity<List<ConversationUnreadCount>> getUnreadCountsByConversation() {
        return ResponseEntity.ok(chatService.getUnreadCountsByConversation(currentUser.getUserId()));
    }

    /**
     * Marks every unread message in this conversation as read for the caller - the REST
     * equivalent of the WebSocket read-marker ping (see ChatWebSocketController#read /
     * ChatServiceImpl#broadcastReadMarker), for any client not maintaining a live STOMP
     * session. Same participant authorization as getHistory/sendMessage.
     */
    @PutMapping("/orders/{orderId}/read")
    public ResponseEntity<Void> markConversationRead(@PathVariable Long orderId) {
        log.info("PUT /chat/orders/{}/read", orderId);
        String role = currentUser.isCourier() ? "COURIER" : "CUSTOMER";
        chatService.markConversationRead(orderId, currentUser.getUserId(), role);
        return ResponseEntity.noContent().build();
    }
}