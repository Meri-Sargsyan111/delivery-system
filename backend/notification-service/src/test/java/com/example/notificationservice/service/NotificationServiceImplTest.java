package com.example.notificationservice.service;

import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private JavaMailSender mailSender;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private NotificationServiceImpl notificationService;

    private static final String RECIPIENT = "notify@example.com";
    private static final String SUBJECT   = "Delivery Notification";

    /**
     * @Value fields are not set by Mockito's @InjectMocks (Spring-only injection).
     * ReflectionTestUtils bridges the gap without touching production code.
     */
    @BeforeEach
    void injectValueFields() {
        ReflectionTestUtils.setField(notificationService, "mailRecipient", RECIPIENT);
        ReflectionTestUtils.setField(notificationService, "mailSubject", SUBJECT);
    }

    @Test
    void add_persistsNotificationEntityWithCorrectMessage() {
        notificationService.add("Order dispatched");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getMessage()).isEqualTo("Order dispatched");
    }

    @Test
    void add_sendsEmailToConfiguredRecipientWithCorrectSubjectAndBody() {
        notificationService.add("Order dispatched");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly(RECIPIENT);
        assertThat(sent.getSubject()).isEqualTo(SUBJECT);
        assertThat(sent.getText()).isEqualTo("Order dispatched");
    }

    @Test
    void add_broadcastsMessageToWebSocketNotificationsTopic() {
        notificationService.add("Order dispatched");

        verify(messagingTemplate).convertAndSend("/topic/notifications", "Order dispatched");
    }

    @Test
    void add_whenRepositoryThrows_propagatesExceptionAndSkipsEmailAndWebSocket() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> notificationService.add("Order dispatched"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void add_whenMailSenderThrows_propagatesExceptionAfterPersist() {
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notificationService.add("Order dispatched"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("SMTP unavailable");

        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void add_whenWebSocketThrows_propagatesExceptionAfterPersistAndEmail() {
        doThrow(new RuntimeException("WebSocket unavailable"))
                .when(messagingTemplate).convertAndSend(eq("/topic/notifications"), any(Object.class));

        assertThatThrownBy(() -> notificationService.add("Order dispatched"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("WebSocket unavailable");

        verify(notificationRepository).save(any(Notification.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void getNotifications_mapsNotificationEntitiesToMessageStrings() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> stored = new PageImpl<>(List.of(
                new Notification("First message"),
                new Notification("Second message")
        ));
        when(notificationRepository.findAll(pageable)).thenReturn(stored);

        Page<String> result = notificationService.getNotifications(pageable);

        assertThat(result.getContent()).containsExactly("First message", "Second message");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void getNotifications_whenNoNotificationsStored_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findAll(pageable)).thenReturn(Page.empty());

        Page<String> result = notificationService.getNotifications(pageable);

        assertThat(result).isEmpty();
    }

    @Test
    void getNotifications_passesPageableToRepository() {
        Pageable pageable = PageRequest.of(1, 5);
        when(notificationRepository.findAll(pageable)).thenReturn(Page.empty());

        notificationService.getNotifications(pageable);

        verify(notificationRepository).findAll(pageable);
    }

    @Test
    void getNotifications_whenRepositoryThrows_propagatesException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findAll(pageable))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> notificationService.getNotifications(pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");
    }
}