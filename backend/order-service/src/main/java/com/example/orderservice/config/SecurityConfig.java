package com.example.orderservice.config;

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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Validates the same RS256 JWTs issued by auth-service (via its JWKS endpoint,
 * see application.yml's jwk-set-uri) for order-service's own HTTP endpoints,
 * so this service is protected even when called directly, bypassing the gateway.
 *
 * Two paths stay public in the JWT sense (permitAll() below), for different reasons:
 *  - GET /orders/{id}/status (numeric id only): courier-service's OrderServiceClient calls
 *    this exact narrow endpoint synchronously to check order status/ownership for
 *    start-delivery, deliver, and rating validation, and carries no credential today. It
 *    deliberately exposes only id/status/ownership ids - never customerName/addresses/
 *    customerPhone - so this remaining unauthenticated surface carries no PII.
 *  - POST /orders/internal/from-payment and PUT /orders/internal/{id}/unassign: called by
 *    payment-service and courier-service respectively, with no end-user JWT to present
 *    (service-to-service calls - courier-service's caller already authenticated the
 *    courier's own accept/reject HTTP call on ITS side). InternalServiceTokenFilter
 *    (registered below) requires a shared-secret header on both paths before they reach
 *    the controller - same pattern as courier-service's reserve endpoint.
 *
 * The full GET /orders/{id} and POST /orders (direct creation) require authentication
 * plus role/ownership authorization, enforced in OrderServiceImpl.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            InternalServiceTokenFilter internalServiceTokenFilter) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalServiceTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/orders/{id:\\d+}/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/orders/internal/from-payment").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/orders/internal/{id:\\d+}/unassign").permitAll()
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