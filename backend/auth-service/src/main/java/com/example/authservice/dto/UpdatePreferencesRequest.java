package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial-update semantics: a null field leaves that preference unchanged rather than
 * clearing it. In practice the frontend's Settings page always sends every field (see
 * auth.service.ts's UserPreferences), but a client only sending the field(s) it's
 * actually changing is also supported (see AuthServiceImpl.updatePreferences). theme/
 * language are validated against a fixed allowlist, not bean-validation annotations,
 * since the allowlist may grow independently of this DTO. The notification-toggle fields
 * are forwarded as-is to notification-service (see NotificationServiceClient), which owns
 * their storage and validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferencesRequest {

    private String theme;
    private String language;
    private Boolean orderUpdatesEnabled;
    private Boolean chatMessagesEnabled;
    private Boolean courierAssignmentEnabled;
    private Boolean paymentNotificationsEnabled;
    private Boolean soundEnabled;
}