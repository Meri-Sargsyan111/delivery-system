package com.example.notificationservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationService {

    private final List<String> notifications = new ArrayList<>();

    private final JavaMailSender mailSender;

    public void add(String notification) {

        notifications.add(notification);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("smeri2455@gmail.com");
        message.setSubject("Delivery System Notification");
        message.setText(notification);

        mailSender.send(message);
    }

    public List<String> getAll() {
        return notifications;
    }
}