package com.example.chatservice.ws;

import com.example.chatservice.security.AuthorityRoles;
import com.example.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP CONNECT frames and authorizes SUBSCRIBE/SEND frames for chat's
 * private, per-order topics - see WebSocketConfig for why this exists (the location/
 * notification WebSocket endpoints elsewhere in this project are public broadcast
 * topics with no equivalent need).
 *
 * A browser's native WebSocket handshake can't carry a Bearer Authorization header
 * (confirmed - see api-gateway's SecurityConfig, which permits the /ws-chat/** handshake
 * path for exactly this reason). Instead, the JWT travels as a STOMP header on the
 * CONNECT frame itself, sent by the client's STOMP library (connectHeaders) after the
 * WebSocket tunnel is already open. The JWT is validated here with the same JwtDecoder/
 * JwtAuthenticationConverter beans the REST endpoints use (see SecurityConfig).
 *
 * The resolved Authentication is cached here keyed by STOMP session id, rather than
 * relying on accessor.setUser() propagating implicitly to later frames on the same
 * session - verified against a real client that this propagation is not reliable in
 * this Spring Boot version for a plain (non-spring-security-messaging) interceptor
 * setup: SUBSCRIBE/SEND frames arrived with accessor.getUser() null even though CONNECT
 * had succeeded. Session-id-keyed caching is the standard, well-documented workaround.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final Pattern SUBSCRIBE_ORDER_ID = Pattern.compile("^/topic/chat/order/(\\d+)$");
    private static final Pattern SEND_ORDER_ID = Pattern.compile("^/app/chat/(\\d+)$");

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ChatService chatService;

    private final ConcurrentMap<String, AbstractAuthenticationToken> sessionAuthentications = new ConcurrentHashMap<>();

    /** Used by ChatWebSocketController to resolve the sender's real identity for a SEND
     *  frame - see this class's javadoc for why the Principal method-argument approach
     *  (which relies on the same unreliable propagation) isn't used instead. */
    public AbstractAuthenticationToken getAuthentication(String sessionId) {
        return sessionAuthentications.get(sessionId);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        String sessionId = accessor.getSessionId();

        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor, sessionId);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/user/")) {

                requireAuthenticated(sessionId);
            } else {
                authorize(sessionId, destination, SUBSCRIBE_ORDER_ID);
            }
        } else if (StompCommand.SEND.equals(command)) {
            authorize(sessionId, accessor.getDestination(), SEND_ORDER_ID);
        } else if (StompCommand.DISCONNECT.equals(command)) {
            sessionAuthentications.remove(sessionId);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor, String sessionId) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT rejected: missing Authorization header");
            throw new MessagingException("Missing Authorization header on STOMP CONNECT");
        }

        try {
            Jwt jwt = jwtDecoder.decode(authHeader.substring(7));
            AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            accessor.setUser(authentication);
            sessionAuthentications.put(sessionId, authentication);
            log.info("STOMP CONNECT authenticated: sessionId={}, sub={}", sessionId, jwt.getSubject());
        } catch (JwtException ex) {
            log.warn("STOMP CONNECT rejected: invalid JWT ({})", ex.getMessage());
            throw new MessagingException("Invalid or expired token", ex);
        }
    }

    private void requireAuthenticated(String sessionId) {
        if (!sessionAuthentications.containsKey(sessionId)) {
            throw new MessagingException("Not authenticated");
        }
    }

    private void authorize(String sessionId, String destination, Pattern destinationPattern) {
        AbstractAuthenticationToken authentication = sessionAuthentications.get(sessionId);
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new MessagingException("Not authenticated");
        }

        Long orderId = extractOrderId(destination, destinationPattern);
        if (orderId == null) {
            throw new MessagingException("Cannot determine chat order from destination: " + destination);
        }

        String role = extractRole(authentication);
        if ("ADMIN".equals(role)) {

            throw new MessagingException("ADMIN chat access is read-only via REST, not available over WebSocket");
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        chatService.requireParticipant(orderId, userId, role);
    }

    private Long extractOrderId(String destination, Pattern pattern) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String extractRole(AbstractAuthenticationToken authentication) {
        return AuthorityRoles.extractRole(authentication)
                .orElseThrow(() -> new MessagingException("Authenticated principal has no role"));
    }
}