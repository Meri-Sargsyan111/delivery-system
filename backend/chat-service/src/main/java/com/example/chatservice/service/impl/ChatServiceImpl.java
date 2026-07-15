package com.example.chatservice.service.impl;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.entity.ChatMessage;
import com.example.chatservice.entity.OrderParticipants;
import com.example.chatservice.exception.ChatNotAvailableException;
import com.example.chatservice.exception.ChatSendingDisabledException;
import com.example.chatservice.mapper.ChatMessageMapper;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.OrderParticipantsRepository;
import com.example.chatservice.security.AuthorityRoles;
import com.example.chatservice.security.CurrentUser;
import com.example.chatservice.service.ChatService;
import com.example.chatservice.ws.ChatChannelInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final Set<String> TERMINAL_STATUSES = Set.of("DELIVERED", "CANCELLED");

    private final ChatMessageRepository chatMessageRepository;
    private final OrderParticipantsRepository orderParticipantsRepository;
    private final CurrentUser currentUser;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatChannelInterceptor chatChannelInterceptor;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * ChatChannelInterceptor already depends on ChatService, so injecting it back here
     * creates a bean cycle - @Lazy breaks it (standard Spring workaround) without
     * touching ChatChannelInterceptor. SimpMessagingTemplate must be @Lazy too: it's
     * produced by the WebSocket message-broker infrastructure, which itself depends on
     * WebSocketConfig -> ChatChannelInterceptor -> ChatService, i.e. a second path back
     * into this same bean - eagerly resolving either parameter re-triggers the cycle.
     */
    public ChatServiceImpl(ChatMessageRepository chatMessageRepository,
                            OrderParticipantsRepository orderParticipantsRepository,
                            CurrentUser currentUser,
                            ChatMessageMapper chatMessageMapper,
                            @Lazy ChatChannelInterceptor chatChannelInterceptor,
                            @Lazy SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.orderParticipantsRepository = orderParticipantsRepository;
        this.currentUser = currentUser;
        this.chatMessageMapper = chatMessageMapper;
        this.chatChannelInterceptor = chatChannelInterceptor;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Page<ChatMessageResponse> getHistory(Long orderId, Pageable pageable) {
        if (!currentUser.isAdmin()) {
            String role = currentUser.isCourier() ? "COURIER" : "CUSTOMER";
            requireParticipant(orderId, currentUser.getUserId(), role);
        }
        return chatMessageRepository.findByOrderIdOrderBySentAtAscIdAsc(orderId, pageable)
                .map(chatMessageMapper::toResponse);
    }

    @Override
    public ChatMessage sendMessage(Long orderId, UUID senderUserId, String senderRole, String content) {
        requireParticipant(orderId, senderUserId, senderRole);
        requireSendable(orderId);
        validateContent(content);

        ChatMessage message = buildMessage(orderId, senderUserId, senderRole, content);

        ChatMessage saved = chatMessageRepository.save(message);
        log.info("Chat message persisted: orderId={}, senderRole={}", orderId, senderRole);
        return saved;
    }

    private void requireSendable(Long orderId) {
        OrderParticipants participants = orderParticipantsRepository.findById(orderId)
                .orElseThrow(() -> new ChatNotAvailableException("Chat is not available for order " + orderId));
        if (TERMINAL_STATUSES.contains(participants.getOrderStatus())) {
            throw new ChatSendingDisabledException("Order " + orderId + " has reached a terminal state ("
                    + participants.getOrderStatus() + "); sending is disabled, history remains readable");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("message content must not be blank");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message content exceeds the maximum length of " + MAX_MESSAGE_LENGTH);
        }
    }

    private ChatMessage buildMessage(Long orderId, UUID senderUserId, String senderRole, String content) {
        ChatMessage message = new ChatMessage();
        message.setOrderId(orderId);
        message.setSenderUserId(senderUserId);
        message.setSenderRole(senderRole);
        message.setContent(content);
        return message;
    }

    @Override
    public void requireParticipant(Long orderId, UUID userId, String role) {
        OrderParticipants participants = orderParticipantsRepository.findById(orderId).orElse(null);

        if (participants == null || participants.getCourierUserId() == null) {
            throw new ChatNotAvailableException(
                    "Chat is not available for order " + orderId + " until a courier is assigned");
        }

        boolean isOwningCustomer = "CUSTOMER".equals(role) && userId.equals(participants.getCustomerUserId());
        boolean isAssignedCourier = "COURIER".equals(role) && userId.equals(participants.getCourierUserId());

        if (!isOwningCustomer && !isAssignedCourier) {
            throw new AccessDeniedException("Not authorized to access chat for order " + orderId);
        }
    }

    @Override
    public void sendFromSession(Long orderId, String sessionId, String content) {
        AbstractAuthenticationToken authentication = chatChannelInterceptor.getAuthentication(sessionId);
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Not authenticated");
        }

        UUID senderUserId = UUID.fromString(jwt.getSubject());
        String senderRole = extractRole(authentication);

        ChatMessage saved = sendMessage(orderId, senderUserId, senderRole, content);
        messagingTemplate.convertAndSend("/topic/chat/order/" + orderId, chatMessageMapper.toResponse(saved));
    }

    private String extractRole(AbstractAuthenticationToken authentication) {
        return AuthorityRoles.extractRole(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated principal has no role"));
    }
}