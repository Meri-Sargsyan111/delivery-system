package com.example.chatservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single place that derives the caller's identity from the validated JWT/SecurityContext.
 * Never trust a userId/role supplied by the client instead of this. Used for REST calls;
 * the WebSocket path derives identity the same way but from a Jwt captured at STOMP
 * CONNECT time (see ChatChannelInterceptor), not from this SecurityContext-based lookup.
 */
@Component
public class CurrentUser {

    public UUID getUserId() {
        return UUID.fromString(jwt().getSubject());
    }

    public boolean hasRole(String role) {
        String authority = "ROLE_" + role;
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isCourier() {
        return hasRole("COURIER");
    }

    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT principal in SecurityContext");
        }
        return jwt;
    }
}