package com.example.apigateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies the reactive SecurityWebFilterChain wiring: ReactiveJwtDecoder is mocked so
 * no network call to auth-service's JWKS endpoint is needed to run this test, and no
 * downstream business service needs to be running (public routes are handled locally by
 * the gateway; protected routes are only checked for a non-401 outcome once past security -
 * this test isn't re-verifying the proxying itself).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityConfigTest {

    private static final String INVALID_TOKEN = "not-a-real-jwt";

    @Autowired private WebTestClient webTestClient;

    @MockBean private ReactiveJwtDecoder reactiveJwtDecoder;

    @BeforeEach
    void setUp() {
        when(reactiveJwtDecoder.decode(eq(INVALID_TOKEN)))
                .thenReturn(Mono.error(new BadJwtException("bad token")));
    }

    @Test
    void protectedRoute_withoutToken_returns401() {
        webTestClient.get().uri("/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_withInvalidToken_returns401() {
        webTestClient.get().uri("/orders")
                .header("Authorization", "Bearer " + INVALID_TOKEN)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void optionsRequest_isAlwaysPermitted() {
        webTestClient.method(HttpMethod.OPTIONS).uri("/orders")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403) {
                        throw new AssertionError("OPTIONS must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void actuatorHealth_staysPublic() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void notificationWebSocketHandshakePath_staysPublic() {

        webTestClient.get().uri("/ws")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/ws must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void courierLocationWebSocketHandshakePath_staysPublic() {
        webTestClient.get().uri("/ws-location")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/ws-location must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void authLogin_withoutToken_isNotBlockedBySecurity() {
        webTestClient.post().uri("/auth/login")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/auth/login must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void authRegister_withoutToken_isNotBlockedBySecurity() {
        webTestClient.post().uri("/auth/register")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/auth/register must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void authRefresh_withoutToken_isNotBlockedByGatewaySecurity() {

        webTestClient.post().uri("/auth/refresh")
                .exchange()
                .expectBody(String.class)
                .consumeWith(result -> {
                    Integer status = result.getStatus().value();
                    String body = result.getResponseBody();
                    boolean looksLikeGatewaySecurityBlock = status == 401 && (body == null || body.isBlank());
                    if (looksLikeGatewaySecurityBlock) {
                        throw new AssertionError(
                                "/auth/refresh appears blocked by gateway security (401 with empty body)");
                    }
                });
    }

    @Test
    void authLogout_withoutToken_isNotBlockedBySecurity() {
        webTestClient.post().uri("/auth/logout")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/auth/logout must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void authWellKnownJwks_withoutToken_isNotBlockedBySecurity() {
        webTestClient.get().uri("/auth/.well-known/jwks.json")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/auth/.well-known/** must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void authMe_withoutToken_returns401() {

        webTestClient.get().uri("/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void chatWebSocketHandshakePath_staysPublic() {

        webTestClient.get().uri("/ws-chat")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/ws-chat must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void chatRest_withoutToken_returns401() {

        webTestClient.get().uri("/chat/orders/1/messages")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}