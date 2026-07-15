package com.example.apigateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-client-IP fixed-window limiter for the credential-facing auth routes, which are
 * permitAll (see SecurityConfig) since login/register/refresh can't require a token to
 * get a token - nothing else in front of auth-service throttles brute-force/credential
 * stuffing attempts. There's no Redis in this stack (single-box deployment, see
 * docker-compose.prod.yml), so this is a plain in-memory counter rather than Gateway's
 * built-in RequestRateLimiter (which requires a RedisRateLimiter); it would need a shared
 * store to remain correct if this service is ever scaled to multiple instances.
 * Relies on server.forward-headers-strategy=framework (see application.yml) so
 * getRemoteAddress() resolves to the real client IP behind Caddy, not Caddy's own address.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthRateLimitingFilter implements WebFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/auth/login", "/auth/register", "/auth/refresh");
    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_MILLIS = 60_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!LIMITED_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        long now = System.currentTimeMillis();
        Window window = windows.compute(clientIp, (ip, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MILLIS) {
                return new Window(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > MAX_REQUESTS_PER_WINDOW) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    private static class Window {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(1);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}