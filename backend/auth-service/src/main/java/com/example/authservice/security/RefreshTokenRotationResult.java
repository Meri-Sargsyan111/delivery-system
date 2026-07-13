package com.example.authservice.security;

import java.util.UUID;

/**
 * Result of successfully rotating a refresh token: the raw value of the newly
 * issued token (to be set as the response cookie) plus the user it belongs to
 * (so a new access token can be minted for them).
 */
public record RefreshTokenRotationResult(String newRawToken, UUID userId) {
}