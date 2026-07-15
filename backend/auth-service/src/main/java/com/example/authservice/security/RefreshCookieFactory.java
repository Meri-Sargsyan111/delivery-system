package com.example.authservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the Set-Cookie header for the refresh token. {@code secure} and {@code sameSite}
 * are configurable (see application.yml) because local development runs over plain HTTP:
 * a browser silently drops any cookie marked Secure unless the page itself is HTTPS, so
 * this must be false for localhost and true once the app is served over HTTPS.
 */
@Component
public class RefreshCookieFactory {

    static final String COOKIE_PATH = "/auth";

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final long refreshTokenTtlSeconds;

    public RefreshCookieFactory(
            @Value("${auth.refresh-cookie.name}") String cookieName,
            @Value("${auth.refresh-cookie.secure}") boolean secure,
            @Value("${auth.refresh-cookie.same-site}") String sameSite,
            @Value("${jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public ResponseCookie build(String rawToken) {
        return baseBuilder()
                .value(rawToken)
                .maxAge(Duration.ofSeconds(refreshTokenTtlSeconds))
                .build();
    }

    /** An expired, empty cookie that instructs the browser to delete the existing one. */
    public ResponseCookie clear() {
        return baseBuilder()
                .value("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder() {
        return ResponseCookie.from(cookieName)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH);
    }
}