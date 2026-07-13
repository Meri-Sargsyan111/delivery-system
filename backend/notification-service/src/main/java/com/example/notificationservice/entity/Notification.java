package com.example.notificationservice.entity;

import jakarta.persistence.*;
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

    public Notification(String message, UUID recipientUserId) {
        this.message = message;
        this.recipientUserId = recipientUserId;
        this.createdAt = LocalDateTime.now();
    }
}
