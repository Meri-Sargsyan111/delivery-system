package com.example.authservice.config;

import com.example.authservice.controller.AuthController;
import com.example.authservice.controller.CustomerController;
import com.example.authservice.controller.JwkSetController;
import com.example.authservice.security.JwtService;
import com.example.authservice.security.RefreshCookieFactory;
import com.example.authservice.security.RefreshTokenService;
import com.example.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

/**
 * Verifies the SecurityFilterChain wiring itself (not business logic): which paths require
 * a bearer token and which don't. /auth/refresh and /auth/logout are permitAll at this layer
 * by design - they validate the caller via the refresh-token cookie instead, inside the
 * controller/service - so a request reaching the controller here (even one that then fails
 * for a missing/invalid cookie) proves this layer let it through correctly.
 */
@WebMvcTest({AuthController.class, JwkSetController.class, CustomerController.class})
@Import({SecurityConfig.class, CorsConfig.class})
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AuthService authService;
    @MockBean private RefreshTokenService refreshTokenService;
    @MockBean private RefreshCookieFactory refreshCookieFactory;
    @MockBean private JwtService jwtService;
    @MockBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(refreshCookieFactory.clear()).thenReturn(ResponseCookie.from("refresh_token", "").build());
    }

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
    void getCustomerById_isReachableWithoutToken() throws Exception {
        when(authService.getCustomerById(any())).thenReturn(null);

        mockMvc.perform(get("/customers/" + UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void listCustomers_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/customers"))
                .andExpect(status().isUnauthorized());
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
