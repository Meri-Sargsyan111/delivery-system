package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The single combined preferences object the frontend's Settings page reads/writes -
 * theme/language are stored locally (see User entity); the notification toggles are
 * aggregated live from notification-service (see NotificationServiceClient) since that
 * remains their system of record. Field names mirror the frontend's UserPreferences
 * TypeScript interface exactly (auth.service.ts).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesResponse {

    private String theme;
    private String language;
    private boolean orderUpdatesEnabled;
    private boolean chatMessagesEnabled;
    private boolean courierAssignmentEnabled;
    private boolean paymentNotificationsEnabled;
    private boolean soundEnabled;
}
