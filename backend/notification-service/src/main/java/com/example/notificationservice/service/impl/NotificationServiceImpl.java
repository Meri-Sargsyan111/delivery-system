package com.example.notificationservice.service.impl;

import com.example.notificationservice.dto.NotificationResponse;
import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.entity.NotificationType;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.security.CurrentUser;
import com.example.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CurrentUser currentUser;

    @Override
    public void add(String message, UUID recipientUserId, NotificationType type, boolean playSound) {
        log.info("WebSocket notification sent: {} (type={}, playSound={})", message, type, playSound);

        Notification saved = notificationRepository.save(
                new Notification(message, recipientUserId, type, playSound));

        messagingTemplate.convertAndSend("/topic/notifications", message);

        if (recipientUserId != null) {
            messagingTemplate.convertAndSendToUser(recipientUserId.toString(), "/queue/notifications", message);
        }

        NotificationResponse structured = toResponse(saved);
        messagingTemplate.convertAndSend("/topic/notifications/structured", structured);
        if (recipientUserId != null) {
            messagingTemplate.convertAndSendToUser(
                    recipientUserId.toString(), "/queue/notifications/structured", structured);
        }
    }

    @Override
    public Page<String> getNotifications(Pageable pageable) {
        if (currentUser.isAdmin()) {
            return notificationRepository.findAll(pageable).map(Notification::getMessage);
        }
        return notificationRepository.findByRecipientUserId(currentUser.getUserId(), pageable)
                .map(Notification::getMessage);
    }

    @Override
    public Page<NotificationResponse> getStructuredNotifications(Pageable pageable) {
        if (currentUser.isAdmin()) {
            return notificationRepository.findAll(pageable).map(this::toResponse);
        }
        return notificationRepository.findByRecipientUserId(currentUser.getUserId(), pageable)
                .map(this::toResponse);
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationType type = notification.getType() != null ? notification.getType() : NotificationType.GENERIC;
        return new NotificationResponse(
                notification.getId(), notification.getMessage(), type,
                notification.isPlaySound(), notification.getCreatedAt());
    }
}