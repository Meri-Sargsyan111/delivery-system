package com.example.authservice.security.impl;

import com.example.authservice.entity.RefreshToken;
import com.example.authservice.exception.InvalidRefreshTokenException;
import com.example.authservice.repository.RefreshTokenRepository;
import com.example.authservice.security.RefreshTokenRotationResult;
import com.example.authservice.security.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshTokenTtlSeconds;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Override
    @Transactional
    public String issue(UUID userId) {
        String rawToken = generateRawToken();
        persist(userId, rawToken);
        return rawToken;
    }

    @Override
    @Transactional
    public RefreshTokenRotationResult rotate(String rawToken) {
        RefreshToken existing = loadValid(rawToken);

        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        String newRawToken = generateRawToken();
        persist(existing.getUserId(), newRawToken);

        return new RefreshTokenRotationResult(newRawToken, existing.getUserId());
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    private RefreshToken loadValid(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new InvalidRefreshTokenException("Missing refresh token");
        }

        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        if (token.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        return token;
    }

    private void persist(UUID userId, String rawToken) {
        Instant now = Instant.now();
        RefreshToken token = new RefreshToken(
                null, userId, hash(rawToken), now, now.plusSeconds(refreshTokenTtlSeconds), null);
        refreshTokenRepository.save(token);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}