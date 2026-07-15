package com.example.chatservice.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Optional;

public final class AuthorityRoles {

    private static final String ROLE_PREFIX = "ROLE_";

    private AuthorityRoles() {
    }

    public static Optional<String> extractRole(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(Object::toString)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .findFirst()
                .map(authority -> authority.substring(ROLE_PREFIX.length()));
    }
}
