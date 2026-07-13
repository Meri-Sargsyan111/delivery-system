package com.example.notificationservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single place that derives the caller's identity from the validated JWT/SecurityContext.
 * Never trust a userId supplied by the client instead of this.
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

    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT principal in SecurityContext");
        }
        return jwt;
    }
}
