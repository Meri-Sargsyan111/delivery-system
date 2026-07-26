package com.example.notificationservice.service;

import com.example.notificationservice.dto.NotificationPreferencesResponse;
import com.example.notificationservice.dto.UpdateNotificationPreferencesRequest;

public interface NotificationPreferencesService {

    /**
     * Returns the current user's notification preferences, creating a default (all-enabled)
     * row on first access if none exists yet.
     */
    NotificationPreferencesResponse getPreferences();

    /**
     * Applies any non-null fields in the request to the current user's preferences,
     * creating a default row first if none exists yet.
     */
    NotificationPreferencesResponse updatePreferences(UpdateNotificationPreferencesRequest request);
}
