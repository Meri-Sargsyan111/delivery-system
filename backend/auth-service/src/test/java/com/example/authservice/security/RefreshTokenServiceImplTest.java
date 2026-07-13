package com.example.authservice.security;

import com.example.authservice.entity.RefreshToken;
import com.example.authservice.exception.InvalidRefreshTokenException;
import com.example.authservice.repository.RefreshTokenRepository;
import com.example.authservice.security.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final long TTL_SECONDS = 604_800L;

    @Mock private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, TTL_SECONDS);
    }

    @Test
    void issue_persistsOnlyAHashNeverTheRawToken() {
        UUID userId = UUID.randomUUID();

        String rawToken = refreshTokenService.issue(userId);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(rawToken).isNotBlank();
    }

    @Test
    void issue_generatesDifferentTokensEachCall() {
        UUID userId = UUID.randomUUID();

        String first = refreshTokenService.issue(userId);
        String second = refreshTokenService.issue(userId);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rotate_validToken_issuesNewAccessCapabilityForSameUser() {
        UUID userId = UUID.randomUUID();
        RefreshToken existing = activeToken(userId);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        RefreshTokenRotationResult result = refreshTokenService.rotate("raw-token-value");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.newRawToken()).isNotBlank();
        assertThat(result.newRawToken()).isNotEqualTo("raw-token-value");
    }

    @Test
    void rotate_validToken_revokesTheOldTokenAtomically() {
        UUID userId = UUID.randomUUID();
        RefreshToken existing = activeToken(userId);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        refreshTokenService.rotate("raw-token-value");

        assertThat(existing.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void rotate_oldTokenCannotBeReusedAfterRotation() {
        UUID userId = UUID.randomUUID();
        RefreshToken existing = activeToken(userId);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        refreshTokenService.rotate("raw-token-value");

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token-value"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void rotate_expiredToken_throwsInvalidRefreshTokenException() {
        RefreshToken expired = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash",
                Instant.now().minusSeconds(TTL_SECONDS + 10), Instant.now().minusSeconds(10), null);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token-value"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rotate_revokedToken_throwsInvalidRefreshTokenException() {
        RefreshToken revoked = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash",
                Instant.now().minusSeconds(100), Instant.now().plusSeconds(TTL_SECONDS), Instant.now());
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token-value"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void rotate_unknownToken_throwsInvalidRefreshTokenException() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("some-malformed-or-unknown-token"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    void rotate_missingToken_throwsInvalidRefreshTokenException() {
        assertThatThrownBy(() -> refreshTokenService.rotate(null))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate("  "))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void revoke_existingActiveToken_marksItRevoked() {
        RefreshToken existing = activeToken(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        refreshTokenService.revoke("raw-token-value");

        assertThat(existing.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void revoke_unknownToken_isANoOp() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        refreshTokenService.revoke("some-unknown-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revoke_missingToken_isANoOpAndNeverQueriesRepository() {
        refreshTokenService.revoke(null);
        refreshTokenService.revoke("");

        verify(refreshTokenRepository, never()).findByTokenHash(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshToken activeToken(UUID userId) {
        return new RefreshToken(UUID.randomUUID(), userId, "existing-hash",
                Instant.now().minusSeconds(100), Instant.now().plusSeconds(TTL_SECONDS), null);
    }
}