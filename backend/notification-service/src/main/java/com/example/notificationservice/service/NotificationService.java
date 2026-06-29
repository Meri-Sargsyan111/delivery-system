package com.example.notificationservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for managing and dispatching notifications.
 *
 * <p>Provides operations to persist a notification, send it via email,
 * broadcast it over WebSocket, and retrieve the notification history.
 */
public interface NotificationService {

    /**
     * Persists the notification, sends it as an email, and broadcasts it
     * to all WebSocket subscribers on {@code /topic/notifications}.
     *
     * @param message the notification text to be delivered
     */
    void add(String message);

    /**
     * Returns a paginated list of all stored notification messages,
     * ordered by persistence time (most recent last).
     *
     * @param pageable pagination and sorting parameters
     * @return a {@link Page} of notification message strings
     */
    Page<String> getNotifications(Pageable pageable);
}
