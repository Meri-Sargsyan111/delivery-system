package com.example.notificationservice.config;

import com.example.notificationservice.controller.NotificationController;
import com.example.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SecurityFilterChain wiring, not business logic: NotificationService is
 * mocked out, and JwtDecoder is mocked so no real key material or network call to
 * auth-service's JWKS endpoint is needed to run this test.
 */
@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private NotificationService notificationService;
    @MockBean private JwtDecoder jwtDecoder;

    @Test
    void getNotifications_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotifications_withMalformedToken_returns401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotifications_withValidToken_isNotRejectedByTheSecurityLayer() throws Exception {
        when(notificationService.getNotifications(any()))
                .thenReturn(new PageImpl<>(List.of("a notification"), PageRequest.of(0, 10), 1));

        MvcResult result = mockMvc.perform(get("/notifications").with(jwt())).andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void webSocketHandshakePath_staysPublic() throws Exception {

        MvcResult result = mockMvc.perform(get("/ws")).andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }
}