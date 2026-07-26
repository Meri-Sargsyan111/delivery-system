package com.example.trackingservice.ws;

import com.example.trackingservice.security.TrackingAccessGuard;
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
 * Authenticates STOMP CONNECT frames and authorizes SUBSCRIBE frames for tracking's
 * per-order topic, mirroring chat-service's ChatChannelInterceptor exactly (same reason
 * for existing: a browser can't attach an Authorization header to the WebSocket
 * handshake, so the JWT travels as a STOMP header on CONNECT instead). Read-only - no
 * SEND destinations exist here, clients only ever subscribe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ORDER_TOPIC = Pattern.compile("^/topic/tracking/order/(\\d+)$");
    private static final String ADMIN_TOPIC = "/topic/tracking/admin/live";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final TrackingAccessGuard trackingAccessGuard;

    private final ConcurrentMap<String, AbstractAuthenticationToken> sessionAuthentications = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        String sessionId = accessor.getSessionId();

        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor, sessionId);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(sessionId, accessor.getDestination());
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

    private void authorizeSubscribe(String sessionId, String destination) {
        AbstractAuthenticationToken authentication = sessionAuthentications.get(sessionId);
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new MessagingException("Not authenticated");
        }

        String role = extractRole(authentication);
        UUID userId = UUID.fromString(jwt.getSubject());

        if (ADMIN_TOPIC.equals(destination)) {
            if (!"ADMIN".equals(role)) {
                throw new MessagingException("Only ADMIN may subscribe to " + ADMIN_TOPIC);
            }
            return;
        }

        Long orderId = extractOrderId(destination);
        if (orderId == null) {
            throw new MessagingException("Cannot determine tracking order from destination: " + destination);
        }

        if (!trackingAccessGuard.isOwnerOrAdmin(orderId, userId, role)) {
            throw new MessagingException("Not authorized to subscribe to tracking for order " + orderId);
        }
    }

    private Long extractOrderId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = ORDER_TOPIC.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String extractRole(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElseThrow(() -> new MessagingException("Authenticated principal has no role"));
    }
}
