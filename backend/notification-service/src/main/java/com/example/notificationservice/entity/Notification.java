package com.example.notificationservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;

    private LocalDateTime createdAt;

    /**
     * The auth-service user id this notification is intended for, when known (derived
     * from the Kafka event that created it - see NotificationConsumer). Null for
     * notifications created before recipient tracking existed; those remain admin-only
     * visible rather than guessed-assigned to anyone (see NotificationServiceImpl).
     */
    private UUID recipientUserId;

    /**
     * Nullable - rows created before this field existed have no type. Read side treats
     * a null type as GENERIC (see NotificationServiceImpl) rather than backfilling.
     */
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    /** Hint for the frontend to play an alert sound (e.g. a courier's new-delivery ping). */
    private boolean playSound;

    public Notification(String message, UUID recipientUserId) {
        this(message, recipientUserId, NotificationType.GENERIC, false);
    }

    public Notification(String message, UUID recipientUserId, NotificationType type, boolean playSound) {
        this.message = message;
        this.recipientUserId = recipientUserId;
        this.createdAt = LocalDateTime.now();
        this.type = type;
        this.playSound = playSound;
    }
}
