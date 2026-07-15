package com.example.notificationservice.service;

import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.security.CurrentUser;
import com.example.notificationservice.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @Mock private NotificationRepository notificationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private CurrentUser currentUser;

    @InjectMocks private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.isAdmin()).thenReturn(true);
        lenient().when(currentUser.getUserId()).thenReturn(ADMIN_ID);
    }

    @Test
    void add_persistsNotificationEntityWithCorrectMessageAndRecipient() {
        notificationService.add("Order dispatched", RECIPIENT_USER_ID);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getMessage()).isEqualTo("Order dispatched");
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo(RECIPIENT_USER_ID);
    }

    @Test
    void add_withNullRecipient_persistsWithNullRecipientUserId() {
        notificationService.add("Order dispatched", null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getRecipientUserId()).isNull();
    }

    @Test
    void add_broadcastsMessageToWebSocketNotificationsTopic() {
        notificationService.add("Order dispatched", RECIPIENT_USER_ID);

        verify(messagingTemplate).convertAndSend("/topic/notifications", "Order dispatched");
    }

    @Test
    void add_whenRecipientKnown_alsoSendsToUserSpecificQueue() {
        notificationService.add("Order dispatched", RECIPIENT_USER_ID);

        verify(messagingTemplate).convertAndSendToUser(
                RECIPIENT_USER_ID.toString(), "/queue/notifications", "Order dispatched");
    }

    @Test
    void add_whenRecipientUnknown_skipsUserSpecificSend() {
        notificationService.add("Order dispatched", null);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void add_whenRepositoryThrows_propagatesExceptionAndSkipsWebSocket() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> notificationService.add("Order dispatched", RECIPIENT_USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void add_whenWebSocketThrows_propagatesExceptionAfterPersist() {
        doThrow(new RuntimeException("WebSocket unavailable"))
                .when(messagingTemplate).convertAndSend(eq("/topic/notifications"), any(Object.class));

        assertThatThrownBy(() -> notificationService.add("Order dispatched", RECIPIENT_USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("WebSocket unavailable");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void getNotifications_asAdmin_returnsAllNotifications() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> stored = new PageImpl<>(List.of(
                new Notification("First message", RECIPIENT_USER_ID),
                new Notification("Second message", OTHER_USER_ID)
        ));
        when(notificationRepository.findAll(pageable)).thenReturn(stored);

        Page<String> result = notificationService.getNotifications(pageable);

        assertThat(result.getContent()).containsExactly("First message", "Second message");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void getNotifications_asNonAdmin_scopesQueryToOwnRecipientUserId() {
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(RECIPIENT_USER_ID);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> stored = new PageImpl<>(List.of(new Notification("Mine", RECIPIENT_USER_ID)));
        when(notificationRepository.findByRecipientUserId(RECIPIENT_USER_ID, pageable)).thenReturn(stored);

        Page<String> result = notificationService.getNotifications(pageable);

        assertThat(result.getContent()).containsExactly("Mine");
        verify(notificationRepository, never()).findAll(pageable);
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