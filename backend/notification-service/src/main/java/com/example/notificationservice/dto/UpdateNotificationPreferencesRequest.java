package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial-update semantics: a null field leaves that preference unchanged rather than
 * clearing it, so a client only sends the field(s) it's actually changing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationPreferencesRequest {

    private Boolean orderUpdates;
    private Boolean chatMessages;
    private Boolean courierAssignment;
    private Boolean paymentNotifications;
    private Boolean soundsEnabled;
}
