package com.example.chatservice.ws;

import com.example.chatservice.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Simulates a real STOMP session: CONNECT first (to populate the interceptor's session-id
 * cache), then SUBSCRIBE/SEND on the SAME session id - exactly the flow a real client
 * follows, and the exact flow that surfaced the accessor.getUser()-propagation bug this
 * design works around (see ChatChannelInterceptor's javadoc).
 */
@ExtendWith(MockitoExtension.class)
class ChatChannelInterceptorTest {

    private static final Long ORDER_ID = 42L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String SESSION_ID = "session-1";

    @Mock private JwtDecoder jwtDecoder;
    @Mock private ChatService chatService;

    private final JwtAuthenticationConverter jwtAuthenticationConverter = realConverter();

    private JwtAuthenticationConverter realConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("role");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    private ChatChannelInterceptor interceptor() {
        return new ChatChannelInterceptor(jwtDecoder, jwtAuthenticationConverter, chatService);
    }

    private Jwt jwtWithRole(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(USER_ID.toString())
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }

    private Message<byte[]> connectMessage(String sessionId, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand command, String sessionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connect_withoutAuthorizationHeader_isRejected() {
        assertThatThrownBy(() -> interceptor().preSend(connectMessage(SESSION_ID, null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connect_withInvalidJwt_isRejected() {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> interceptor().preSend(connectMessage(SESSION_ID, "Bearer bad-token"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribe_afterValidConnect_delegatesToParticipantCheck() {
        when(jwtDecoder.decode("good-token")).thenReturn(jwtWithRole("ROLE_CUSTOMER"));
        doNothing().when(chatService).requireParticipant(ORDER_ID, USER_ID, "CUSTOMER");
        ChatChannelInterceptor interceptor = interceptor();

        interceptor.preSend(connectMessage(SESSION_ID, "Bearer good-token"), null);
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, SESSION_ID, "/topic/chat/order/" + ORDER_ID), null);

        verify(chatService).requireParticipant(ORDER_ID, USER_ID, "CUSTOMER");
    }

    @Test
    void subscribe_whenNotAParticipant_isRejected() {
        when(jwtDecoder.decode("good-token")).thenReturn(jwtWithRole("ROLE_CUSTOMER"));
        doThrow(new org.springframework.security.access.AccessDeniedException("not authorized"))
                .when(chatService).requireParticipant(eq(ORDER_ID), any(), any());
        ChatChannelInterceptor interceptor = interceptor();
        interceptor.preSend(connectMessage(SESSION_ID, "Bearer good-token"), null);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, SESSION_ID, "/topic/chat/order/" + ORDER_ID), null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void subscribe_withoutPriorConnect_isRejected() {
        assertThatThrownBy(() -> interceptor().preSend(
                frame(StompCommand.SUBSCRIBE, "never-connected-session", "/topic/chat/order/" + ORDER_ID), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribe_asAdmin_isRejectedOutright() {
        when(jwtDecoder.decode("admin-token")).thenReturn(jwtWithRole("ROLE_ADMIN"));
        ChatChannelInterceptor interceptor = interceptor();
        interceptor.preSend(connectMessage(SESSION_ID, "Bearer admin-token"), null);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, SESSION_ID, "/topic/chat/order/" + ORDER_ID), null))
                .isInstanceOf(MessagingException.class);

        verify(chatService, never()).requireParticipant(any(), any(), any());
    }

    @Test
    void send_afterValidConnect_delegatesToParticipantCheck() {
        when(jwtDecoder.decode("good-token")).thenReturn(jwtWithRole("ROLE_COURIER"));
        doNothing().when(chatService).requireParticipant(ORDER_ID, USER_ID, "COURIER");
        ChatChannelInterceptor interceptor = interceptor();

        interceptor.preSend(connectMessage(SESSION_ID, "Bearer good-token"), null);
        interceptor.preSend(frame(StompCommand.SEND, SESSION_ID, "/app/chat/" + ORDER_ID), null);

        verify(chatService).requireParticipant(ORDER_ID, USER_ID, "COURIER");
    }

    @Test
    void subscribe_toOwnPrivateErrorQueue_isAllowedWithoutOrderParticipantCheck() {

        when(jwtDecoder.decode("good-token")).thenReturn(jwtWithRole("ROLE_CUSTOMER"));
        ChatChannelInterceptor interceptor = interceptor();
        interceptor.preSend(connectMessage(SESSION_ID, "Bearer good-token"), null);

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, SESSION_ID, "/user/queue/chat/errors"), null);

        verify(chatService, never()).requireParticipant(any(), any(), any());
    }

    @Test
    void subscribe_toPrivateErrorQueue_withoutPriorConnect_isRejected() {
        assertThatThrownBy(() -> interceptor().preSend(
                frame(StompCommand.SUBSCRIBE, "never-connected-session", "/user/queue/chat/errors"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void disconnect_clearsSessionCache_subsequentFrameOnSameIdIsRejected() {
        when(jwtDecoder.decode("good-token")).thenReturn(jwtWithRole("ROLE_CUSTOMER"));
        ChatChannelInterceptor interceptor = interceptor();
        interceptor.preSend(connectMessage(SESSION_ID, "Bearer good-token"), null);
        interceptor.preSend(frame(StompCommand.DISCONNECT, SESSION_ID, null), null);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, SESSION_ID, "/topic/chat/order/" + ORDER_ID), null))
                .isInstanceOf(MessagingException.class);
    }
}