package com.example.trackingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Validates the same RS256 JWTs issued by auth-service (via its JWKS endpoint,
 * see application.yml's jwk-set-uri) for tracking-service's own HTTP endpoints,
 * so this service is protected even when called directly, bypassing the gateway.
 * courier-service now calls GET /tracking/{orderId}/route synchronously (see
 * TrackingServiceClient there), authenticated with the caller's own forwarded JWT -
 * no new service-to-service credential needed, this stays under anyRequest().authenticated().
 *
 * /ws-tracking/** stays public at this HTTP layer, same reason as chat-service's
 * /ws-chat: browsers cannot attach an Authorization header to a native WebSocket
 * handshake. Real authentication/authorization happens in TrackingChannelInterceptor
 * (JWT on the STOMP CONNECT frame, SUBSCRIBE authorized per order).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/ws-tracking/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    /**
     * Mirrors auth-service's own converter exactly: the "role" claim is a single
     * already-prefixed string (e.g. "ROLE_CUSTOMER"), not the default OAuth2 "scope" claim.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("role");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}