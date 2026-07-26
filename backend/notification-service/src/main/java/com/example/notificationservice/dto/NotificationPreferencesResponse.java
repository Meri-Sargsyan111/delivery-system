package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesResponse {

    private boolean orderUpdates;
    private boolean chatMessages;
    private boolean courierAssignment;
    private boolean paymentNotifications;
    private boolean soundsEnabled;
}