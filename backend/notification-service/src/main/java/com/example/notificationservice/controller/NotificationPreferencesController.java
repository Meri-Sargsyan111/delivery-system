package com.example.notificationservice.controller;

import com.example.notificationservice.dto.NotificationPreferencesResponse;
import com.example.notificationservice.dto.UpdateNotificationPreferencesRequest;
import com.example.notificationservice.service.NotificationPreferencesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications/preferences")
public class NotificationPreferencesController {

    private final NotificationPreferencesService notificationPreferencesService;

    @GetMapping
    public ResponseEntity<NotificationPreferencesResponse> getPreferences() {
        return ResponseEntity.ok(notificationPreferencesService.getPreferences());
    }

    @PutMapping
    public ResponseEntity<NotificationPreferencesResponse> updatePreferences(
            @RequestBody UpdateNotificationPreferencesRequest request) {
        return ResponseEntity.ok(notificationPreferencesService.updatePreferences(request));
    }
}