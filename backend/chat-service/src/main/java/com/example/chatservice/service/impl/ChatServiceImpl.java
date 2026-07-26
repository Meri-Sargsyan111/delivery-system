package com.example.chatservice.service.impl;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ConversationSummaryResponse;
import com.example.chatservice.dto.ConversationUnreadCount;
import com.example.chatservice.dto.ReadMarkerBroadcast;
import com.example.chatservice.dto.TypingBroadcast;
import com.example.chatservice.dto.UnreadCountUpdate;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        OrderParticipants participants = loadParticipants(orderId);
        authorizeParticipant(participants, senderUserId, senderRole);
        requireSendable(participants);
        validateContent(content);

        ChatMessage message = buildMessage(orderId, senderUserId, senderRole, content, participants);

        ChatMessage saved = chatMessageRepository.save(message);
        log.info("Chat message persisted: orderId={}, senderRole={}", orderId, senderRole);
        pushUnreadCountUpdate(saved.getOrderId(), saved.getReceiverUserId());
        return saved;
    }

    private void requireSendable(OrderParticipants participants) {
        if (TERMINAL_STATUSES.contains(participants.getOrderStatus())) {
            throw new ChatSendingDisabledException("Order " + participants.getOrderId() + " has reached a terminal state ("
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

    private ChatMessage buildMessage(Long orderId, UUID senderUserId, String senderRole, String content,
                                      OrderParticipants participants) {
        ChatMessage message = new ChatMessage();
        message.setOrderId(orderId);
        message.setSenderUserId(senderUserId);
        message.setSenderRole(senderRole);
        message.setContent(content);
        message.setReceiverUserId(resolveReceiverUserId(senderRole, participants));
        message.setRead(false);
        return message;
    }

    /** The other participant: the courier if this message was sent by the customer, or
     *  vice versa - this is who the new message is addressed to for unread-tracking. */
    private UUID resolveReceiverUserId(String senderRole, OrderParticipants participants) {
        return "CUSTOMER".equals(senderRole) ? participants.getCourierUserId() : participants.getCustomerUserId();
    }

    @Override
    public void requireParticipant(Long orderId, UUID userId, String role) {
        OrderParticipants participants = loadParticipants(orderId);
        authorizeParticipant(participants, userId, role);
    }

    private OrderParticipants loadParticipants(Long orderId) {
        OrderParticipants participants = orderParticipantsRepository.findById(orderId).orElse(null);

        if (participants == null || participants.getCourierUserId() == null) {
            throw new ChatNotAvailableException(
                    "Chat is not available for order " + orderId + " until a courier is assigned");
        }
        return participants;
    }

    private void authorizeParticipant(OrderParticipants participants, UUID userId, String role) {
        boolean isOwningCustomer = "CUSTOMER".equals(role) && userId.equals(participants.getCustomerUserId());
        boolean isAssignedCourier = "COURIER".equals(role) && userId.equals(participants.getCourierUserId());

        if (!isOwningCustomer && !isAssignedCourier) {
            throw new AccessDeniedException("Not authorized to access chat for order " + participants.getOrderId());
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

    @Override
    public void broadcastTyping(Long orderId, String sessionId) {
        IdentifiedSender sender = resolveSender(orderId, sessionId);
        messagingTemplate.convertAndSend(
                "/topic/chat/order/" + orderId + "/typing", new TypingBroadcast(sender.userId()));
    }

    @Override
    @Transactional
    public void broadcastReadMarker(Long orderId, String sessionId, Long lastReadMessageId) {
        IdentifiedSender sender = resolveSender(orderId, sessionId);
        markConversationReadInternal(orderId, sender.userId());
        messagingTemplate.convertAndSend(
                "/topic/chat/order/" + orderId + "/read",
                new ReadMarkerBroadcast(sender.userId(), lastReadMessageId));
    }

    /**
     * Shared by the two ephemeral relays above: resolves identity exactly like
     * sendFromSession does, then re-checks participancy directly (defense in depth -
     * never trust ChatChannelInterceptor's authorization alone, same discipline sendMessage
     * already applies for the persisted send path).
     */
    private IdentifiedSender resolveSender(Long orderId, String sessionId) {
        AbstractAuthenticationToken authentication = chatChannelInterceptor.getAuthentication(sessionId);
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Not authenticated");
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        String role = extractRole(authentication);
        requireParticipant(orderId, userId, role);

        return new IdentifiedSender(userId, role);
    }

    @Override
    public long getUnreadCount(UUID userId) {
        return chatMessageRepository.countByReceiverUserIdAndReadFalse(userId);
    }

    @Override
    public List<ConversationUnreadCount> getUnreadCountsByConversation(UUID userId) {
        return chatMessageRepository.countUnreadGroupedByOrderId(userId);
    }

    @Override
    @Transactional
    public void markConversationRead(Long orderId, UUID userId, String role) {
        requireParticipant(orderId, userId, role);
        markConversationReadInternal(orderId, userId);
    }

    /**
     * Bulk-marks every unread message addressed to userId in this conversation as read (see
     * ChatMessageRepository.markAsReadForRecipient - a single UPDATE, no per-message
     * load-then-save), then pushes the user's own updated counts to /user/queue/chat/unread
     * so their badge stays in sync without polling. A no-op (no push) when there was nothing
     * to mark, so re-opening an already-read conversation doesn't spam a pointless update.
     * Callers (broadcastReadMarker, markConversationRead) are both @Transactional - required
     * for the @Modifying bulk update to execute at all.
     */
    private void markConversationReadInternal(Long orderId, UUID userId) {
        int updated = chatMessageRepository.markAsReadForRecipient(orderId, userId);
        if (updated > 0) {
            pushUnreadCountUpdate(orderId, userId);
        }
    }

    /**
     * Best-effort, per-user targeted push - never lets a WebSocket delivery failure (or the
     * target simply not being connected right now) affect the caller's own operation, same
     * discipline this codebase already applies to Kafka publish failures elsewhere. Reused
     * for both directions (§4 of the unread-notification feature): pushed to the RECEIVER
     * right after a new message is persisted (sendMessage), and to the READER right after
     * their own mark-as-read completes (markConversationReadInternal) - in both cases the
     * counts queried here reflect the true current state at push time.
     *
     * Published as a plain broadcast to /topic/chat/unread/{userId}, not via
     * SimpMessagingTemplate.convertAndSendToUser - see ChatChannelInterceptor's javadoc on
     * UNREAD_TOPIC_PREFIX for why (accessor.setUser() is already documented, and was
     * verified again here, as unreliable for cross-frame/cross-request delivery in this
     * setup). The interceptor authorizes SUBSCRIBE to this topic so only the matching
     * authenticated user can ever listen on their own id.
     */
    private void pushUnreadCountUpdate(Long orderId, UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            long conversationUnread = chatMessageRepository.countByOrderIdAndReceiverUserIdAndReadFalse(orderId, userId);
            long totalUnread = chatMessageRepository.countByReceiverUserIdAndReadFalse(userId);
            messagingTemplate.convertAndSend("/topic/chat/unread/" + userId,
                    new UnreadCountUpdate(orderId, conversationUnread, totalUnread));
        } catch (Exception e) {
            log.error("Failed to push unread-count update for orderId={}, userId={}", orderId, userId, e);
        }
    }

    @Override
    public List<ConversationSummaryResponse> getConversations(UUID userId) {
        List<OrderParticipants> participants =
                orderParticipantsRepository.findByCustomerUserIdOrCourierUserId(userId, userId);

        if (participants.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = participants.stream().map(OrderParticipants::getOrderId).toList();

        Map<Long, ChatMessage> latestByOrderId = chatMessageRepository.findLatestMessageForOrders(orderIds).stream()
                .collect(Collectors.toMap(ChatMessage::getOrderId, message -> message));

        Map<Long, Long> unreadByOrderId = chatMessageRepository.countUnreadGroupedByOrderId(userId).stream()
                .collect(Collectors.toMap(ConversationUnreadCount::orderId, ConversationUnreadCount::unreadCount));

        return participants.stream()
                .map(p -> {
                    ChatMessage latest = latestByOrderId.get(p.getOrderId());
                    return new ConversationSummaryResponse(
                            p.getOrderId(),
                            p.getCustomerName(),
                            p.getCourierName(),
                            latest != null ? latest.getContent() : null,
                            latest != null ? latest.getSentAt() : null,
                            unreadByOrderId.getOrDefault(p.getOrderId(), 0L));
                })
                .sorted(Comparator.comparing(
                        ConversationSummaryResponse::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private record IdentifiedSender(UUID userId, String role) {}
}