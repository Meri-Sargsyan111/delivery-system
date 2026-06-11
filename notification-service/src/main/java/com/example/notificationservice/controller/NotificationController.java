package com.example.notificationservice.controller;

import com.example.notificationservice.NotificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationStore notificationStore;

    @GetMapping("/notifications")
    public List<String> getNotifications() {
        return notificationStore.getAll();
    }
}