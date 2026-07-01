package com.example.notificationservice.controller;

import com.example.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationStore;

    @GetMapping("/notifications")
    public ResponseEntity<Page<String>> getNotifications(@PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(notificationStore.getNotifications(pageable));
    }
}
