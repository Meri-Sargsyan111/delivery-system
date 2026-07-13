package com.example.authservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SecurityFilterChain wiring itself (not business logic): which paths require
 * a bearer token and which don't. /auth/refresh and /auth/logout are permitAll at this layer
 * by design - they validate the caller via the refresh-token cookie instead, inside the
 * controller/service - so a request reaching the controller here (even one that then fails
 * for a missing/invalid cookie) proves this layer let it through correctly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/auth/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_isReachableWithoutToken() throws Exception {

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/auth/login must never be blocked by security, got " + status);
                    }
                });
    }

    @Test
    void refresh_isReachableWithoutBearerToken() throws Exception {

        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_isReachableWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    void jwks_isPublic() throws Exception {
        mockMvc.perform(get("/auth/.well-known/jwks.json"))
                .andExpect(status().isOk());
    }

    @Test
    void optionsPreflight_isNeverBlocked() throws Exception {
        mockMvc.perform(options("/auth/refresh")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("OPTIONS must never be blocked by security, got " + status);
                    }
                });
    }
}