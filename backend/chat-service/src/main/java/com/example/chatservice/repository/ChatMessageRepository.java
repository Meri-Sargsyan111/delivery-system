package com.example.chatservice.repository;

import com.example.chatservice.dto.ConversationUnreadCount;
import com.example.chatservice.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByOrderIdOrderBySentAtAscIdAsc(Long orderId, Pageable pageable);

    /** Grand-total unread count for a user, across every conversation - backed by
     *  idx_chat_messages_receiver_unread (receiverUserId, read), a single indexed count. */
    long countByReceiverUserIdAndReadFalse(UUID receiverUserId);

    /** Unread count for one specific conversation - same index, additionally filtered by orderId. */
    long countByOrderIdAndReceiverUserIdAndReadFalse(Long orderId, UUID receiverUserId);

    /** One row per conversation that has at least one unread message for this user - a single
     *  GROUP BY over the same index, no join against OrderParticipants needed. */
    @Query("SELECT new com.example.chatservice.dto.ConversationUnreadCount(m.orderId, COUNT(m)) " +
            "FROM ChatMessage m WHERE m.receiverUserId = :userId AND m.read = false GROUP BY m.orderId")
    List<ConversationUnreadCount> countUnreadGroupedByOrderId(@Param("userId") UUID userId);

    /**
     * Bulk-marks every unread message addressed to userId in one order as read, in a single
     * UPDATE - no per-message load-then-save round trips. Requires an active transaction
     * (see ChatServiceImpl.broadcastReadMarker / markConversationRead, both @Transactional).
     * Returns the number of rows actually updated, so callers can skip a pointless
     * unread-count push when there was nothing to mark.
     */
    @Modifying
    @Query("UPDATE ChatMessage m SET m.read = true, m.readAt = CURRENT_TIMESTAMP " +
            "WHERE m.orderId = :orderId AND m.receiverUserId = :userId AND m.read = false")
    int markAsReadForRecipient(@Param("orderId") Long orderId, @Param("userId") UUID userId);

    /**
     * The single latest message (by id, which is also insertion order here) for each of the
     * given orders - used by the conversation-list endpoint. A plain JPQL "greatest-n-per-group"
     * isn't supported, hence the native subquery joining each order_id to its own MAX(id).
     * Callers must not pass an empty orderIds list (an empty native IN(...) is invalid SQL).
     */
    @Query(value = "SELECT m.* FROM chat_messages m " +
            "INNER JOIN (SELECT order_id, MAX(id) AS max_id FROM chat_messages " +
            "WHERE order_id IN (:orderIds) GROUP BY order_id) latest " +
            "ON m.id = latest.max_id",
            nativeQuery = true)
    List<ChatMessage> findLatestMessageForOrders(@Param("orderIds") List<Long> orderIds);
}
