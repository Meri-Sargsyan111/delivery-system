package com.example.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Validates the RS256 JWTs already issued by auth-service (via its JWKS endpoint,
 * see application.yml's jwk-set-uri) for every route proxied by the gateway.
 *
 * WebSocket handshake paths (/ws/**, /ws-tracking/**, /ws-chat/**) are intentionally left
 * public: browsers cannot attach an Authorization header to a native WebSocket handshake,
 * so requiring a bearer token here would break Notifications, Live Tracking, and Chat
 * outright. For /ws this means the connection itself stays unauthenticated end-to-end
 * (documented limitation - notification broadcasts are public). /ws-tracking and /ws-chat
 * are different: each service's own ChannelInterceptor (TrackingChannelInterceptor,
 * ChatChannelInterceptor) authenticates the STOMP CONNECT frame (JWT carried as a STOMP
 * header, not an HTTP header, so the gateway's inability to see it here doesn't matter)
 * and authorizes every SUBSCRIBE against real order participation.
 *
 * POST /payments/webhook/** is also left public for a different reason: Stripe (and any
 * future provider) calls it directly with no JWT at all, authenticated instead by an
 * SDK-verified request signature checked inside payment-service itself (see
 * payment-service's WebhookController/PaymentServiceImpl) - this is not an
 * unauthenticated hole, just authentication enforced deeper in the stack than the gateway.
 *
 * CORS is wired in here via {@code .cors(...)}, backed by the {@link CorsConfigurationSource}
 * bean below, rather than left solely to {@code spring.cloud.gateway.globalcors}: that
 * routing-layer filter never runs for requests Spring Security itself rejects (401/403),
 * so browsers received no Access-Control-Allow-Origin header on those responses and failed
 * the request as a CORS error before the JSON body was ever visible to application code.
 * Registering CORS through Security guarantees it applies to every response this chain
 * produces, including its own authentication failures.
 */
@Configuration
@EnableWebFluxSecurity
public class
SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http, ReactiveJwtDecoder reactiveJwtDecoder,
            ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter) {

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/ws/**", "/ws-tracking/**", "/ws-chat/**").permitAll()
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/payments/webhook/**").permitAll()

                        .pathMatchers("/auth/register", "/auth/login", "/auth/refresh",
                                "/auth/logout", "/auth/.well-known/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(reactiveJwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    /**
     * Mirrors what was previously configured only under spring.cloud.gateway.globalcors
     * in application.yml (same origin/methods/headers) - moved here so Spring Security
     * applies it to every response, not just ones that reach the gateway's routing filter.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200", "http://178.105.214.109"));
        configuration.setAllowedOriginPatterns(List.of("https://*.trycloudflare.com"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * The "role" claim is a single already-prefixed string (e.g. "ROLE_CUSTOMER"), not the
     * default OAuth2 "scope" claim - mirrors auth-service's own SecurityConfig exactly so
     * both sides agree on how authorities are derived from the same token.
     */
    @Bean
    public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("role");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}