package com.example.chatservice.config;

import com.example.chatservice.controller.ChatController;
import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ConversationUnreadCount;
import com.example.chatservice.security.CurrentUser;
import com.example.chatservice.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SecurityFilterChain wiring, not business logic: ChatService is mocked
 * out, and JwtDecoder is mocked so no real key material or network call to
 * auth-service's JWKS endpoint is needed to run this test - mirrors tracking-service's
 * equivalent test exactly.
 */
@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ChatService chatService;
    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private CurrentUser currentUser;

    @Test
    void getHistory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/chat/orders/1/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHistory_withMalformedToken_returns401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/chat/orders/1/messages").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHistory_withValidToken_reachesController() throws Exception {
        Page<ChatMessageResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(chatService.getHistory(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/chat/orders/1/messages").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void getHistory_whenServiceDeniesOwnership_returns403() throws Exception {
        when(chatService.getHistory(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenThrow(new AccessDeniedException("not a participant"));

        mockMvc.perform(get("/chat/orders/1/messages").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUnreadCount_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/chat/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUnreadCount_withValidToken_returnsTotal() throws Exception {
        when(currentUser.getUserId()).thenReturn(UUID.randomUUID());
        when(chatService.getUnreadCount(any())).thenReturn(3L);

        mockMvc.perform(get("/chat/unread-count").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnread").value(3));
    }

    @Test
    void getUnreadCountsByConversation_withValidToken_returnsList() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUser.getUserId()).thenReturn(userId);
        when(chatService.getUnreadCountsByConversation(userId))
                .thenReturn(List.of(new ConversationUnreadCount(7L, 2L)));

        mockMvc.perform(get("/chat/unread-counts").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(7))
                .andExpect(jsonPath("$[0].unreadCount").value(2));
    }

    @Test
    void markConversationRead_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/chat/orders/1/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markConversationRead_withValidToken_returnsNoContent() throws Exception {
        when(currentUser.getUserId()).thenReturn(UUID.randomUUID());
        when(currentUser.isCourier()).thenReturn(false);

        mockMvc.perform(put("/chat/orders/1/read").with(jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void markConversationRead_whenServiceDeniesOwnership_returns403() throws Exception {
        when(currentUser.getUserId()).thenReturn(UUID.randomUUID());
        when(currentUser.isCourier()).thenReturn(false);
        org.mockito.Mockito.doThrow(new AccessDeniedException("not a participant"))
                .when(chatService).markConversationRead(any(), any(), any());

        mockMvc.perform(put("/chat/orders/1/read").with(jwt()))
                .andExpect(status().isForbidden());
    }
}