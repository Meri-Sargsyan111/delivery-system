package com.example.chatservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_order_id", columnList = "orderId"),
        @Index(name = "idx_chat_messages_receiver_unread", columnList = "receiverUserId, read")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    /** Always derived from the authenticated JWT/security context - never client-supplied. */
    private UUID senderUserId;

    /** "ROLE_CUSTOMER" or "ROLE_COURIER" - also server-determined, never client-supplied. */
    private String senderRole;

    private String content;

    private LocalDateTime sentAt;

    /**
     * The other participant in this order's conversation (customer if senderRole is
     * COURIER, courier if senderRole is CUSTOMER) - resolved and denormalized at send time
     * (see ChatServiceImpl.resolveReceiverUserId) specifically so unread-count queries never
     * need a join against OrderParticipants, just an indexed WHERE on this column. Null on
     * messages sent before this field existed.
     */
    private UUID receiverUserId;

    /**
     * Read/unread state for the receiver. New messages default to false (unread) - set
     * explicitly in ChatServiceImpl.buildMessage, never relying on the column default below.
     * That column default (true) exists purely for the migration: existing rows predating
     * this feature are backfilled as already-read, so shipping this doesn't retroactively
     * surface a flood of "unread" history for every past conversation.
     */
    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private boolean read;

    /** Set when this message transitions to read (see ChatServiceImpl.markConversationReadInternal). Null while unread. */
    private LocalDateTime readAt;

    @PrePersist
    void onCreate() {
        sentAt = LocalDateTime.now();
    }
}