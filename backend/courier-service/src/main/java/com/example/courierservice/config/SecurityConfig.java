package com.example.courierservice.config;

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
 * see application.yml's jwk-set-uri) for courier-service's own HTTP endpoints,
 * so this service is protected even when called directly, bypassing the gateway.
 *
 * Two paths stay public, both deliberately, both pre-existing gaps not introduced here:
 *  - PUT /courier/{courierId}/reserve/{orderId}: order-service's CourierServiceClient calls
 *    this synchronously during assignment and carries no credential today. Locking it down
 *    would break the working courier-assignment flow with no compatible replacement in this
 *    phase's scope - deferred service-to-service-auth gap.
 *  - /ws-location/**: the STOMP/WebSocket handshake. Browsers cannot attach an Authorization
 *    header to a native WebSocket handshake, so requiring a bearer token here would break
 *    Live Tracking outright. WebSocket auth is explicitly out of scope for this phase.
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
                        .requestMatchers("/ws-location/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/courier/{courierId:\\d+}/reserve/{orderId:\\d+}").permitAll()
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