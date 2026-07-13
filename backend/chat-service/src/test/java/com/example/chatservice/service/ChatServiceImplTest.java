package com.example.chatservice.service;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.entity.ChatMessage;
import com.example.chatservice.entity.OrderParticipants;
import com.example.chatservice.exception.ChatNotAvailableException;
import com.example.chatservice.exception.ChatSendingDisabledException;
import com.example.chatservice.mapper.ChatMessageMapper;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.OrderParticipantsRepository;
import com.example.chatservice.security.CurrentUser;
import com.example.chatservice.service.impl.ChatServiceImpl;
import com.example.chatservice.ws.ChatChannelInterceptor;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceImplTest {

    private static final Long ORDER_ID = 42L;
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final UUID OTHER_COURIER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private OrderParticipantsRepository orderParticipantsRepository;
    @Mock private CurrentUser currentUser;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private ChatChannelInterceptor chatChannelInterceptor;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private ChatServiceImpl chatService;

    private OrderParticipants assignedParticipants() {
        return new OrderParticipants(ORDER_ID, CUSTOMER_ID, COURIER_ID, "IN_PROGRESS");
    }

    @BeforeEach
    void defaultToAdmin() {
        lenient().when(currentUser.isAdmin()).thenReturn(true);
        lenient().when(currentUser.getUserId()).thenReturn(ADMIN_ID);
    }

    private void asCustomer(UUID userId) {
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.isCourier()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(userId);
    }

    private void asCourier(UUID userId) {
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.isCourier()).thenReturn(true);
        lenient().when(currentUser.getUserId()).thenReturn(userId);
    }

    @Test
    void getHistory_asOwningCustomer_returnsMessages() {
        asCustomer(CUSTOMER_ID);
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));
        Pageable pageable = PageRequest.of(0, 20);
        ChatMessage message = new ChatMessage();
        ChatMessageResponse response = new ChatMessageResponse(1L, ORDER_ID, CUSTOMER_ID, "CUSTOMER", "hi", null);
        when(chatMessageRepository.findByOrderIdOrderBySentAtAscIdAsc(ORDER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(chatMessageMapper.toResponse(message)).thenReturn(response);

        assertThat(chatService.getHistory(ORDER_ID, pageable).getContent()).containsExactly(response);
    }

    @Test
    void getHistory_asUnrelatedCustomer_throwsAccessDenied() {
        asCustomer(OTHER_CUSTOMER_ID);
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));

        assertThatThrownBy(() -> chatService.getHistory(ORDER_ID, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getHistory_asAssignedCourier_returnsMessages() {
        asCourier(COURIER_ID);
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));
        Pageable pageable = PageRequest.of(0, 20);
        when(chatMessageRepository.findByOrderIdOrderBySentAtAscIdAsc(ORDER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(chatService.getHistory(ORDER_ID, pageable)).isNotNull();
    }

    @Test
    void getHistory_asUnrelatedCourier_throwsAccessDenied() {
        asCourier(OTHER_COURIER_ID);
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));

        assertThatThrownBy(() -> chatService.getHistory(ORDER_ID, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getHistory_asAdmin_bypassesParticipantCheck() {
        Pageable pageable = PageRequest.of(0, 20);
        when(chatMessageRepository.findByOrderIdOrderBySentAtAscIdAsc(ORDER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        chatService.getHistory(ORDER_ID, pageable);

        verify(orderParticipantsRepository, never()).findById(any());
    }

    @Test
    void getHistory_beforeCourierAssigned_throwsChatNotAvailable() {
        asCustomer(CUSTOMER_ID);
        when(orderParticipantsRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new OrderParticipants(ORDER_ID, CUSTOMER_ID, null, "CREATED")));

        assertThatThrownBy(() -> chatService.getHistory(ORDER_ID, PageRequest.of(0, 20)))
                .isInstanceOf(ChatNotAvailableException.class);
    }

    @Test
    void getHistory_returnsChronologicalPageFromRepository() {
        Pageable pageable = PageRequest.of(1, 10);
        Page<ChatMessage> expected = new PageImpl<>(List.of(new ChatMessage(), new ChatMessage()));
        when(chatMessageRepository.findByOrderIdOrderBySentAtAscIdAsc(ORDER_ID, pageable)).thenReturn(expected);

        Page<ChatMessageResponse> result = chatService.getHistory(ORDER_ID, pageable);

        assertThat(result.getContent()).hasSize(2);
        verify(chatMessageRepository).findByOrderIdOrderBySentAtAscIdAsc(ORDER_ID, pageable);
    }

    @Test
    void sendMessage_asOwningCustomer_persistsAndReturnsMessage() {
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessage result = chatService.sendMessage(ORDER_ID, CUSTOMER_ID, "CUSTOMER", "Hello, where are you?");

        assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.getSenderUserId()).isEqualTo(CUSTOMER_ID);
        assertThat(result.getSenderRole()).isEqualTo("CUSTOMER");
        assertThat(result.getContent()).isEqualTo("Hello, where are you?");
    }

    @Test
    void sendMessage_asAssignedCourier_persistsSuccessfully() {
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessage result = chatService.sendMessage(ORDER_ID, COURIER_ID, "COURIER", "I am on the way.");

        assertThat(result.getSenderRole()).isEqualTo("COURIER");
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_asUnrelatedCustomer_throwsAccessDeniedAndDoesNotPersist() {
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));

        assertThatThrownBy(() -> chatService.sendMessage(ORDER_ID, OTHER_CUSTOMER_ID, "CUSTOMER", "hi"))
                .isInstanceOf(AccessDeniedException.class);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_asUnrelatedCourier_throwsAccessDeniedAndDoesNotPersist() {
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));

        assertThatThrownBy(() -> chatService.sendMessage(ORDER_ID, OTHER_COURIER_ID, "COURIER", "hi"))
                .isInstanceOf(AccessDeniedException.class);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_blankContent_throwsIllegalArgumentAndDoesNotPersist() {
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));

        assertThatThrownBy(() -> chatService.sendMessage(ORDER_ID, CUSTOMER_ID, "CUSTOMER", "   "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_oversizedContent_throwsIllegalArgumentAndDoesNotPersist() {
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));
        String tooLong = "x".repeat(2001);

        assertThatThrownBy(() -> chatService.sendMessage(ORDER_ID, CUSTOMER_ID, "CUSTOMER", tooLong))
                .isInstanceOf(IllegalArgumentException.class);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_beforeCourierAssigned_throwsChatNotAvailable() {
        when(orderParticipantsRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new OrderParticipants(ORDER_ID, CUSTOMER_ID, null, "CREATED")));

        assertThatThrownBy(() -> chatService.sendMessage(ORDER_ID, CUSTOMER_ID, "CUSTOMER", "hi"))
                .isInstanceOf(ChatNotAvailableException.class);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_onDeliveredOrder_throwsChatSendingDisabledButHistoryStaysIntact() {
        when(orderParticipantsRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new OrderParticipants(ORDER_ID, CUSTOMER_ID, COURIER_ID, "DELIVERED")));

        assertThatThrownBy(() -> chatService.sendMessage(ORDER_ID, CUSTOMER_ID, "CUSTOMER", "hi"))
                .isInstanceOf(ChatSendingDisabledException.class);

        verify(chatMessageRepository, never()).save(any());
        verify(chatMessageRepository, never()).deleteAll();
    }

    @Test
    void sendMessage_onCancelledOrder_throwsChatSendingDisabled() {
        when(orderParticipantsRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new OrderParticipants(ORDER_ID, CUSTOMER_ID, COURIER_ID, "CANCELLED")));

        assertThatThrownBy(() -> chatService.sendMessage(ORDER_ID, CUSTOMER_ID, "CUSTOMER", "hi"))
                .isInstanceOf(ChatSendingDisabledException.class);
    }

    @Test
    void getHistory_onDeliveredOrder_stillReadableByParticipant() {
        asCustomer(CUSTOMER_ID);
        when(orderParticipantsRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(new OrderParticipants(ORDER_ID, CUSTOMER_ID, COURIER_ID, "DELIVERED")));
        Pageable pageable = PageRequest.of(0, 20);
        when(chatMessageRepository.findByOrderIdOrderBySentAtAscIdAsc(ORDER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(new ChatMessage())));

        assertThat(chatService.getHistory(ORDER_ID, pageable).getContent()).hasSize(1);
    }

    @Test
    void sendMessage_senderIdentityCannotBeSpoofed_persistsExactlyWhatCallerPassedIn() {

        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        when(chatMessageRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        chatService.sendMessage(ORDER_ID, CUSTOMER_ID, "CUSTOMER", "pretend I'm the courier");

        assertThat(captor.getValue().getSenderUserId()).isEqualTo(CUSTOMER_ID);
        assertThat(captor.getValue().getSenderRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void requireParticipant_noOwnershipRecordAtAll_throwsChatNotAvailable() {
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.requireParticipant(ORDER_ID, CUSTOMER_ID, "CUSTOMER"))
                .isInstanceOf(ChatNotAvailableException.class);
    }

    private Jwt jwtFor(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("role", "ROLE_CUSTOMER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }

    @Test
    void sendFromSession_resolvesIdentityFromSessionPersistsAndBroadcasts() {
        AbstractAuthenticationToken authentication =
                new TestingAuthenticationToken(jwtFor(CUSTOMER_ID), null, "ROLE_CUSTOMER");
        when(chatChannelInterceptor.getAuthentication("session-1")).thenReturn(authentication);
        when(orderParticipantsRepository.findById(ORDER_ID)).thenReturn(Optional.of(assignedParticipants()));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        ChatMessageResponse response = new ChatMessageResponse(1L, ORDER_ID, CUSTOMER_ID, "CUSTOMER", "hello", null);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(response);

        chatService.sendFromSession(ORDER_ID, "session-1", "hello");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getSenderUserId()).isEqualTo(CUSTOMER_ID);
        assertThat(captor.getValue().getSenderRole()).isEqualTo("CUSTOMER");
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/order/" + ORDER_ID), eq(response));
    }

    @Test
    void sendFromSession_whenNoAuthenticationForSession_throwsIllegalStateAndDoesNotPersist() {
        when(chatChannelInterceptor.getAuthentication("unknown-session")).thenReturn(null);

        assertThatThrownBy(() -> chatService.sendFromSession(ORDER_ID, "unknown-session", "hi"))
                .isInstanceOf(IllegalStateException.class);

        verify(chatMessageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}