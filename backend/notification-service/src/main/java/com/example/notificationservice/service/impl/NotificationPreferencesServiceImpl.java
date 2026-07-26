package com.example.notificationservice.service.impl;

import com.example.notificationservice.dto.NotificationPreferencesResponse;
import com.example.notificationservice.dto.UpdateNotificationPreferencesRequest;
import com.example.notificationservice.entity.NotificationPreferences;
import com.example.notificationservice.repository.NotificationPreferencesRepository;
import com.example.notificationservice.security.CurrentUser;
import com.example.notificationservice.service.NotificationPreferencesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferencesServiceImpl implements NotificationPreferencesService {

    private final NotificationPreferencesRepository preferencesRepository;
    private final CurrentUser currentUser;

    @Override
    public NotificationPreferencesResponse getPreferences() {
        return toResponse(loadOrCreate(currentUser.getUserId()));
    }

    @Override
    public NotificationPreferencesResponse updatePreferences(UpdateNotificationPreferencesRequest request) {
        NotificationPreferences preferences = loadOrCreate(currentUser.getUserId());

        if (request.getOrderUpdates() != null) {
            preferences.setOrderUpdates(request.getOrderUpdates());
        }
        if (request.getChatMessages() != null) {
            preferences.setChatMessages(request.getChatMessages());
        }
        if (request.getCourierAssignment() != null) {
            preferences.setCourierAssignment(request.getCourierAssignment());
        }
        if (request.getPaymentNotifications() != null) {
            preferences.setPaymentNotifications(request.getPaymentNotifications());
        }
        if (request.getSoundsEnabled() != null) {
            preferences.setSoundsEnabled(request.getSoundsEnabled());
        }

        return toResponse(preferencesRepository.save(preferences));
    }

    private NotificationPreferences loadOrCreate(UUID userId) {
        return preferencesRepository.findById(userId)
                .orElseGet(() -> new NotificationPreferences(userId));
    }

    private NotificationPreferencesResponse toResponse(NotificationPreferences preferences) {
        return new NotificationPreferencesResponse(
                preferences.isOrderUpdates(),
                preferences.isChatMessages(),
                preferences.isCourierAssignment(),
                preferences.isPaymentNotifications(),
                preferences.isSoundsEnabled());
    }
}