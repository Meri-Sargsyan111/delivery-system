package com.example.trackingservice.config;

import com.example.trackingservice.controller.TrackingController;
import com.example.trackingservice.mapper.TrackingEventMapper;
import com.example.trackingservice.service.TrackingService;
import com.example.trackingservice.service.TrackingStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SecurityFilterChain wiring, not business logic: TrackingService/
 * TrackingEventMapper are mocked out, and JwtDecoder is mocked so no real key material
 * or network call to auth-service's JWKS endpoint is needed to run this test.
 * courier-service now calls GET /tracking/{orderId}/route synchronously with the
 * caller's own forwarded JWT (see TrackingServiceClient there) - no service-to-service
 * credential carve-out needed, so /tracking/** stays authenticated except /ws-tracking/**.
 */
@WebMvcTest(TrackingController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TrackingService trackingService;
    @MockBean private TrackingStateService trackingStateService;
    @MockBean private TrackingEventMapper trackingEventMapper;
    @MockBean private JwtDecoder jwtDecoder;

    @Test
    void getTracking_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/tracking/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTracking_withMalformedToken_returns401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/tracking/1").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTracking_withValidToken_reachesController() throws Exception {
        when(trackingService.getTracking(1L)).thenReturn(List.of());
        when(trackingEventMapper.toResponseList(List.of())).thenReturn(List.of());

        mockMvc.perform(get("/tracking/1").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void getTracking_whenServiceDeniesOwnership_returns403() throws Exception {
        when(trackingService.getTracking(1L)).thenThrow(new AccessDeniedException("not authorized"));

        mockMvc.perform(get("/tracking/1").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllEvents_whenServiceDeniesNonAdmin_returns403() throws Exception {

        when(trackingService.getAllEvents(any())).thenThrow(new AccessDeniedException("admin only"));

        mockMvc.perform(get("/tracking")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllEvents_withAdminRole_reachesController() throws Exception {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(trackingService.getAllEvents(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));

        mockMvc.perform(get("/tracking")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}