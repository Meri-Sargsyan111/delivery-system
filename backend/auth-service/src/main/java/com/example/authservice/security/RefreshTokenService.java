package com.example.authservice.security;

import java.util.UUID;

public interface RefreshTokenService {

    /**
     * Issues a brand-new refresh token for the given user and persists only its hash.
     *
     * @return the raw token value, to be handed to the client exactly once (as a cookie)
     */
    String issue(UUID userId);

    /**
     * Validates the given raw refresh token (exists, not expired, not revoked), atomically
     * revokes it, and issues a replacement (rotation) for the same user.
     *
     * @throws com.example.authservice.exception.InvalidRefreshTokenException if the token is
     *         missing, malformed, unknown, expired, or already revoked
     */
    RefreshTokenRotationResult rotate(String rawToken);

    /**
     * Revokes the given raw refresh token, if it exists and isn't already revoked.
     * Silently no-ops for a missing/unknown/already-revoked token so logout stays idempotent.
     */
    void revoke(String rawToken);

    /**
     * Revokes every still-valid refresh token belonging to the given user, so all of their
     * existing sessions are logged out. Used by account deletion.
     */
    void revokeAllForUser(UUID userId);
}