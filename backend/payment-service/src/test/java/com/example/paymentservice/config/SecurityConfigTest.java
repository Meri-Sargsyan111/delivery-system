package com.example.paymentservice.config;

import com.example.paymentservice.controller.PaymentController;
import com.example.paymentservice.controller.WebhookController;
import com.example.paymentservice.dto.PaymentDetailsResponse;
import com.example.paymentservice.entity.PaymentMethodType;
import com.example.paymentservice.entity.PaymentProvider;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SecurityFilterChain wiring, not business logic: PaymentService is mocked
 * out, and JwtDecoder is mocked so no real key material or network call to auth-service's
 * JWKS endpoint is needed to run this test.
 */
@WebMvcTest(controllers = {PaymentController.class, WebhookController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PaymentService paymentService;
    @MockBean private JwtDecoder jwtDecoder;

    @Test
    void getPayment_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/payments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPayment_withMalformedToken_returns401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/payments/" + UUID.randomUUID()).header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPayment_withValidToken_reachesController() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.getDetails(id)).thenReturn(new PaymentDetailsResponse(
                id, id.toString(), PaymentProvider.STRIPE, PaymentMethodType.VISA, PaymentStatus.SUCCEEDED,
                "AMD", BigDecimal.TEN, UUID.randomUUID(), 1L, "pi_1", LocalDateTime.now(), LocalDateTime.now(), null));

        mockMvc.perform(get("/payments/" + id).with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void refund_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(post("/payments/" + UUID.randomUUID() + "/refund")
                        .contentType("application/json")
                        .content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookEndpoint_withoutToken_staysPublic() throws Exception {
        mockMvc.perform(post("/payments/webhook/stripe")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/payments/webhook/** must never be blocked by security, got " + status);
                    }
                });
    }
}
