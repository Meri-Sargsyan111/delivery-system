package com.example.chatservice.service;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ConversationSummaryResponse;
import com.example.chatservice.dto.ConversationUnreadCount;
import com.example.chatservice.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    /**
     * Ephemeral typing ping - resolves the sender the same way sendFromSession does, then
     * broadcasts to /topic/chat/order/{orderId}/typing with no persistence whatsoever.
     */
    void broadcastTyping(Long orderId, String sessionId);

    /**
     * Read-marker ping sent when the receiver views a conversation: marks every unread
     * message in it as read (see markConversationRead), then broadcasts
     * ReadMarkerBroadcast(userId, lastReadMessageId) to /topic/chat/order/{orderId}/read so
     * the other participant's client can update its read-receipt UI live. lastReadMessageId
     * is relayed as-is in the broadcast (unchanged from before this feature existed) but does
     * not gate what gets persisted - every unread message addressed to this user in the
     * conversation is marked read, matching "opening the conversation marks it all read".
     */
    void broadcastReadMarker(Long orderId, String sessionId, Long lastReadMessageId);

    /** Grand total unread count for a user, across every conversation they participate in. */
    long getUnreadCount(UUID userId);

    /** One entry per conversation that currently has at least one unread message for this user. */
    List<ConversationUnreadCount> getUnreadCountsByConversation(UUID userId);

    /**
     * REST entry point for marking a conversation read (see ChatController) - same
     * participant authorization as sendMessage/getHistory, then the same bulk mark-as-read
     * + unread-count push as the WebSocket read-marker path.
     *
     * @throws com.example.chatservice.exception.ChatNotAvailableException if no courier is assigned yet
     * @throws org.springframework.security.access.AccessDeniedException if the caller is not a participant
     */
    void markConversationRead(Long orderId, UUID userId, String role);

    /**
     * Every conversation the given user participates in (as customer or courier), each with
     * its latest message (if any) and current unread count, sorted by most recent activity
     * first - conversations with no messages yet sort last.
     */
    List<ConversationSummaryResponse> getConversations(UUID userId);
}