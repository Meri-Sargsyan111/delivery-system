package com.example.chatservice.service;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ChatService {

    /**
     * ADMIN sees any order's history (read-only oversight, consistent with admin's
     * existing full visibility into orders/tracking/notifications elsewhere in this
     * project). CUSTOMER/COURIER only their own order's history, once a courier is
     * assigned. Throws AccessDeniedException for a non-participant, ChatNotAvailableException
     * if no courier is assigned yet.
     */
    Page<ChatMessageResponse> getHistory(Long orderId, Pageable pageable);

    /**
     * senderUserId/senderRole are supplied by the caller (resolved from the authenticated
     * identity at the transport boundary - see ChatWebSocketController), never trusted from
     * message content. Rejects a non-participant (AccessDeniedException), a blank/oversized
     * message (IllegalArgumentException / bean validation), or a send attempt on a
     * DELIVERED/CANCELLED order (ChatSendingDisabledException). ADMIN may never call this -
     * admin access is read-only by design, see getHistory.
     */
    ChatMessage sendMessage(Long orderId, UUID senderUserId, String senderRole, String content);

    /**
     * Used by ChatChannelInterceptor to authorize a STOMP SUBSCRIBE/SEND before any
     * message-specific logic runs. Same rule as sendMessage's participant check, without
     * persisting anything.
     */
    void requireParticipant(Long orderId, UUID userId, String role);

    /**
     * Resolves the sender's identity from the given STOMP session (see
     * ChatChannelInterceptor), validates and persists the message via sendMessage, and
     * broadcasts it to /topic/chat/order/{orderId}.
     *
     * @throws IllegalStateException if the session has no resolved authentication
     *         (should not happen in practice - ChatChannelInterceptor rejects the
     *         CONNECT frame otherwise)
     */
    void sendFromSession(Long orderId, String sessionId, String content);
}