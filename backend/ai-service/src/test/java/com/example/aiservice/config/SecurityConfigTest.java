package com.example.aiservice.config;

import com.example.aiservice.controller.AiChatController;
import com.example.aiservice.service.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SecurityFilterChain wiring, not business logic: AiChatService is mocked
 * out, and JwtDecoder is mocked so no real key material or network call to auth-service's
 * JWKS endpoint is needed to run this test.
 */
@WebMvcTest(AiChatController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AiChatService aiChatService;
    @MockBean private JwtDecoder jwtDecoder;

    private static final String VALID_BODY = "{\"message\":\"How much to send a 5kg package?\"}";

    @Test
    void chat_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chat_withMalformedToken_returns401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer not-a-real-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chat_withValidToken_reachesController() throws Exception {
        when(aiChatService.chat("How much to send a 5kg package?")).thenReturn("It depends on distance.");

        mockMvc.perform(post("/api/ai/chat")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void chat_withBlankMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
