package com.example.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the "role" claim (e.g. "ROLE_ADMIN") is mapped straight to a matching
 * Spring GrantedAuthority with no extra prefix - mirrors auth-service's own converter.
 * Tested directly against the adapter (no reactive test infrastructure needed): the
 * adapter delegates synchronously to the wrapped JwtAuthenticationConverter.
 */
class JwtAuthenticationConverterTest {

    private final ReactiveJwtAuthenticationConverterAdapter converter =
            new SecurityConfig().jwtAuthenticationConverter();

    @Test
    void convert_mapsRoleClaimToMatchingAuthorityWithNoPrefix() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("role", "ROLE_ADMIN")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        AbstractAuthenticationToken authentication = converter.convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }
}