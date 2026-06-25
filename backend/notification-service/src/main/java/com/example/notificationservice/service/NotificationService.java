package com.example.notificationservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationService {

    private final List<String> notifications = new ArrayList<>();

    private final JavaMailSender mailSender;
    private final SimpMessagingTemplate messagingTemplate;

    public void add(String notification) {

        System.out.println("WEBSOCKET SEND: " + notification);

        notifications.add(notification);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("smeri2455@gmail.com");
        message.setSubject("Delivery System Notification");
        message.setText(notification);

        mailSender.send(message);

        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    public List<String> getAll() {
        return notifications;
    }

    public List<String> getNotifications(int page, int size) {

        int start = page * size;

        if (start >= notifications.size()) {
            return List.of();
        }

        int end = Math.min(start + size, notifications.size());

        return notifications.subList(start, end);
    }
}