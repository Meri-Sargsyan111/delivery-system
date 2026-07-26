package com.example.authservice.client;

/** Mirrors notification-service's NotificationPreferencesResponse field-for-field. */
public record NotificationPreferencesLookupResult(
        boolean orderUpdates,
        boolean chatMessages,
        boolean courierAssignment,
        boolean paymentNotifications,
        boolean soundsEnabled
) {
    /** Safe defaults used when notification-service can't be reached for a GET - see NotificationServiceClient. */
    public static NotificationPreferencesLookupResult defaults() {
        return new NotificationPreferencesLookupResult(true, true, true, true, true);
    }
}