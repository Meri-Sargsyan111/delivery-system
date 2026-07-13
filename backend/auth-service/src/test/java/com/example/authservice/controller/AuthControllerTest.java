package com.example.authservice.controller;

import com.example.authservice.dto.AuthResponse;
import com.example.authservice.dto.LoginRequest;
import com.example.authservice.dto.UserResponse;
import com.example.authservice.entity.Role;
import com.example.authservice.exception.InvalidRefreshTokenException;
import com.example.authservice.security.RefreshCookieFactory;
import com.example.authservice.security.RefreshTokenRotationResult;
import com.example.authservice.security.RefreshTokenService;
import com.example.authservice.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focused on the controller's orchestration (which services it calls, which cookie it
 * sets) rather than on Spring Security enforcement itself - security filters are disabled
 * here since /auth/refresh and /auth/logout are permitAll at that layer by design and
 * validate the caller's session themselves, via the cookie.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private RefreshTokenService refreshTokenService;
    @MockBean private RefreshCookieFactory refreshCookieFactory;

    @Test
    void login_success_issuesRefreshTokenAndSetsCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthResponse authResponse = new AuthResponse("access-jwt", "Bearer", 900L, userResponse(userId));

        when(authService.login(any())).thenReturn(authResponse);
        when(refreshTokenService.issue(userId)).thenReturn("raw-refresh-token");
        when(refreshCookieFactory.build("raw-refresh-token"))
                .thenReturn(cookie("raw-refresh-token", 604_800));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("john@example.com", "Str0ng!Pass"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("raw-refresh-token")))
                .andExpect(content().json("{\"accessToken\":\"access-jwt\",\"tokenType\":\"Bearer\",\"expiresIn\":900}"));

        verify(refreshTokenService).issue(userId);
    }

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(refreshTokenService, never()).rotate(any());
    }

    @Test
    void refresh_validCookie_rotatesAndReturnsNewAccessToken() throws Exception {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.rotate("old-raw-token"))
                .thenReturn(new RefreshTokenRotationResult("new-raw-token", userId));
        when(authService.issueAccessToken(userId))
                .thenReturn(new AuthResponse("new-access-jwt", "Bearer", 900L, userResponse(userId)));
        when(refreshCookieFactory.build("new-raw-token")).thenReturn(cookie("new-raw-token", 604_800));

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE_NAME, "old-raw-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("new-raw-token")))
                .andExpect(content().json("{\"accessToken\":\"new-access-jwt\"}"));
    }

    @Test
    void refresh_expiredOrRevokedOrUnknownToken_returns401() throws Exception {
        when(refreshTokenService.rotate("bad-token"))
                .thenThrow(new InvalidRefreshTokenException("Refresh token has expired"));

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE_NAME, "bad-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withCookie_revokesAndClearsCookie() throws Exception {
        when(refreshCookieFactory.clear()).thenReturn(cookie("", 0));

        mockMvc.perform(post("/auth/logout").cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE_NAME, "some-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(refreshTokenService).revoke("some-token");
    }

    @Test
    void logout_withoutCookie_isIdempotentAndStillSucceeds() throws Exception {
        when(refreshCookieFactory.clear()).thenReturn(cookie("", 0));

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk());

        verify(refreshTokenService).revoke(isNull());
    }

    private UserResponse userResponse(UUID id) {
        return new UserResponse(id, "John", "Doe", "john@example.com", "+1111111111", Role.ROLE_CUSTOMER, null);
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }
}