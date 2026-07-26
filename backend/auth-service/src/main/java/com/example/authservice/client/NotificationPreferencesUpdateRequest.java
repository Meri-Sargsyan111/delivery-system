package com.example.authservice.client;

/** Mirrors notification-service's UpdateNotificationPreferencesRequest field-for-field. */
public record NotificationPreferencesUpdateRequest(
        Boolean orderUpdates,
        Boolean chatMessages,
        Boolean courierAssignment,
        Boolean paymentNotifications,
        Boolean soundsEnabled
) {
}
