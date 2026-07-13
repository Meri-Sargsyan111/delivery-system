package com.example.notificationservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for managing and dispatching notifications.
 *
 * <p>Provides operations to persist a notification, send it via email,
 * broadcast it over WebSocket, and retrieve the notification history.
 */
public interface NotificationService {

    /**
     * Persists the notification, sends it as an email, and broadcasts it
     * to all WebSocket subscribers on {@code /topic/notifications} (unchanged,
     * preserved for existing clients), plus - when the recipient is known - privately
     * to {@code /user/{recipientUserId}/queue/notifications}.
     *
     * @param message the notification text to be delivered
     * @param recipientUserId the auth-service user id this notification is for, or
     *                        {@code null} if unknown (e.g. legacy event shape)
     */
    void add(String message, UUID recipientUserId);

    /**
     * Returns a paginated list of notification messages visible to the caller: ADMIN
     * sees every notification; any other authenticated role sees only notifications
     * recorded for their own user id (notifications with no recorded recipient are
     * admin-only, never guessed-assigned to anyone).
     *
     * @param pageable pagination and sorting parameters
     * @return a {@link Page} of notification message strings
     */
    Page<String> getNotifications(Pageable pageable);
}
