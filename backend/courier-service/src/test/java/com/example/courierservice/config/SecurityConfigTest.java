package com.example.courierservice.config;

import com.example.courierservice.controller.CourierController;
import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.dto.CourierResponse;
import com.example.courierservice.service.AvatarStorageService;
import com.example.courierservice.service.CourierAssignmentService;
import com.example.courierservice.service.CourierRatingService;
import com.example.courierservice.service.CourierService;
import com.example.courierservice.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SecurityFilterChain wiring, not business logic: all service dependencies
 * are mocked out, and JwtDecoder is mocked so no real key material or network call to
 * auth-service's JWKS endpoint is needed to run this test.
 */
@WebMvcTest(CourierController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CourierService courierService;
    @MockBean private LocationService locationService;
    @MockBean private CourierAssignmentService courierAssignmentService;
    @MockBean private CourierRatingService courierRatingService;
    @MockBean private AvatarStorageService avatarStorageService;
    @MockBean private JwtDecoder jwtDecoder;

    @Test
    void listCouriers_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/courier"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listCouriers_withMalformedToken_returns401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/courier").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listCouriers_withValidToken_reachesController() throws Exception {
        when(courierAssignmentService.listCouriers(any(), any())).thenReturn(new PageImpl<>(
                List.of(new CourierResponse(1L, "Alice", CourierStatus.AVAILABLE, null, 0, null)),
                PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/courier").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void createCourier_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/courier")
                        .contentType("application/json")
                        .content("{\"name\":\"New Courier\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeStatus_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/courier/1/status").param("status", "OFFLINE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rateOrder_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/courier/rating/1")
                        .contentType("application/json")
                        .content("{\"value\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reserveCourier_withoutInternalToken_returns401() throws Exception {

        mockMvc.perform(put("/courier/1/reserve/2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reserveCourier_withInvalidInternalToken_returns401() throws Exception {

        mockMvc.perform(put("/courier/1/reserve/2")
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reserveCourier_withValidInternalToken_staysUnauthenticatedButAllowed() throws Exception {

        when(courierAssignmentService.reserveCourier(1L, 2L)).thenReturn(null);

        mockMvc.perform(put("/courier/1/reserve/2")
                        .header("X-Internal-Token", "dev-internal-service-token-change-me"))
                .andExpect(status().isOk());
    }

    @Test
    void createCourier_withCustomerRole_returns403() throws Exception {
        mockMvc.perform(post("/courier")
                        .contentType("application/json")
                        .content("{\"name\":\"New Courier\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rateOrder_withCourierRole_returns403() throws Exception {
        mockMvc.perform(post("/courier/rating/1")
                        .contentType("application/json")
                        .content("{\"value\":5}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCourier_withCourierRole_returns403() throws Exception {

        mockMvc.perform(post("/courier")
                        .contentType("application/json")
                        .content("{\"name\":\"New Courier\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadMyAvatar_withoutToken_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/courier/me/avatar").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadMyAvatar_withCustomerRole_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/courier/me/avatar").file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadMyAvatar_withCourierRole_reachesController() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(courierAssignmentService.uploadMyAvatar(any())).thenReturn(
                new CourierResponse(1L, "Alice", CourierStatus.AVAILABLE, null, 0, "/courier/1/avatar"));

        mockMvc.perform(multipart("/courier/me/avatar").file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isOk());
    }

    @Test
    void uploadAvatarForCourier_withoutToken_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart(HttpMethod.PUT, "/courier/1/avatar").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadAvatarForCourier_withCourierRole_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart(HttpMethod.PUT, "/courier/1/avatar").file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadAvatarForCourier_withAdminRole_reachesController() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(courierAssignmentService.uploadAvatarForCourier(any(), any())).thenReturn(
                new CourierResponse(1L, "Alice", CourierStatus.AVAILABLE, null, 0, "/courier/1/avatar"));

        mockMvc.perform(multipart(HttpMethod.PUT, "/courier/1/avatar").file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void getAvatar_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/courier/1/avatar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAvatar_withAnyAuthenticatedRole_reachesController() throws Exception {
        when(avatarStorageService.load(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/courier/1/avatar")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());
    }
}