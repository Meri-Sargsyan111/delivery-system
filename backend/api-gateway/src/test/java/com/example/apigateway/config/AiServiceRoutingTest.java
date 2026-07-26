package com.example.apigateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression test for a real bug: EstimateController is mapped at /ai/estimate (not
 * /api/ai/estimate) in ai-service, but the frontend calls POST /api/ai/estimate. Without
 * a RewritePath filter, the gateway forwarded that path unchanged, so ai-service 404'd
 * with "No endpoint found for POST /api/ai/estimate". Covers all three ai-service
 * routes (ai-service-chat, ai-service-estimate-api, ai-service-estimate-direct) as
 * separate, single-purpose routes - none of them share a Path predicate with another.
 * This test stands up a tiny stub HTTP server in place of ai-service and asserts the
 * gateway actually forwards each to the right upstream path - unlike SecurityConfigTest,
 * this exercises the real proxying, not just the security filter chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AiServiceRoutingTest {

    private static final String VALID_TOKEN = "valid-test-jwt";
    private static HttpServer stubAiService;
    private static BlockingQueue<String> receivedPaths;

    @Autowired private WebTestClient webTestClient;

    @MockBean private ReactiveJwtDecoder reactiveJwtDecoder;

    @DynamicPropertySource
    static void aiServiceUrl(DynamicPropertyRegistry registry) throws IOException {
        stubAiService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        receivedPaths = new ArrayBlockingQueue<>(10);
        stubAiService.createContext("/", exchange -> {
            receivedPaths.add(exchange.getRequestURI().getPath());
            byte[] body = "{}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stubAiService.start();
        registry.add("AI_SERVICE_URL", () -> "http://localhost:" + stubAiService.getAddress().getPort());
    }

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256")
                .claim("sub", "00000000-0000-0000-0000-000000000000")
                .claim("role", "ROLE_CUSTOMER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(reactiveJwtDecoder.decode(eq(VALID_TOKEN))).thenReturn(Mono.just(jwt));
    }

    @AfterEach
    void drainQueue() {
        receivedPaths.clear();
    }

    @Test
    void estimateCall_underApiPrefix_isRewrittenBeforeReachingAiService() throws InterruptedException {
        webTestClient.post().uri("/api/ai/estimate")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isOk();

        String receivedPath = receivedPaths.poll(5, TimeUnit.SECONDS);
        assertThat(receivedPath).isEqualTo("/ai/estimate");
    }

    @Test
    void estimateCall_withoutApiPrefix_reachesAiServiceUnchanged() throws InterruptedException {
        webTestClient.post().uri("/ai/estimate")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isOk();

        String receivedPath = receivedPaths.poll(5, TimeUnit.SECONDS);
        assertThat(receivedPath).isEqualTo("/ai/estimate");
    }

    @Test
    void chatCall_isForwardedAsIsWithoutRewrite() throws InterruptedException {
        webTestClient.post().uri("/api/ai/chat")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isOk();

        String receivedPath = receivedPaths.poll(5, TimeUnit.SECONDS);
        assertThat(receivedPath).isEqualTo("/api/ai/chat");
    }
}