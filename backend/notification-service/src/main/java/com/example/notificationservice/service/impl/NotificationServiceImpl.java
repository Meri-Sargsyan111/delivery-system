package com.example.notificationservice.service.impl;

import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.security.CurrentUser;
import com.example.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    @Value("${notification.mail.recipient}")
    private String mailRecipient;

    @Value("${notification.mail.subject}")
    private String mailSubject;

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final SimpMessagingTemplate messagingTemplate;
    private final CurrentUser currentUser;

    @Override
    public void add(String message, UUID recipientUserId) {
        log.info("WebSocket notification sent: {}", message);

        notificationRepository.save(new Notification(message, recipientUserId));

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(mailRecipient);
            mailMessage.setSubject(mailSubject);
            mailMessage.setText(message);

            mailSender.send(mailMessage);
        } catch (Exception ex) {

            log.warn("Failed to send email notification, continuing without it: {}", ex.getMessage());
        }

        messagingTemplate.convertAndSend("/topic/notifications", message);

        if (recipientUserId != null) {
            messagingTemplate.convertAndSendToUser(recipientUserId.toString(), "/queue/notifications", message);
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
}