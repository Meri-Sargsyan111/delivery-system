package com.example.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One row per user, created lazily on first read/write (see NotificationPreferencesServiceImpl)
 * rather than at registration time - this service has no signal for when a user is created.
 * Storage/CRUD only: these flags are not yet wired into the actual notification dispatch path
 * (NotificationServiceImpl.add / the Kafka consumers), which is an intentional, literal scope
 * decision - the requirement only asked for settings storage, not enforcement.
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notification_preferences")
public class NotificationPreferences {

    @Id
    private UUID userId;

    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private boolean orderUpdates = true;

    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private boolean chatMessages = true;

    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private boolean courierAssignment = true;

    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private boolean paymentNotifications = true;

    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private boolean soundsEnabled = true;

    public NotificationPreferences(UUID userId) {
        this.userId = userId;
    }
}