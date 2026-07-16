package com.example.orderservice.config;

import com.example.orderservice.controller.OrderController;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
/**
 * Verifies the SecurityFilterChain wiring, not business logic: OrderService is mocked
 * out, and JwtDecoder is mocked so no real key material or network call to
 * auth-service's JWKS endpoint is needed to run this test.
 */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;
    @MockBean private JwtDecoder jwtDecoder;

    @Test
    void listOrders_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listOrders_withMalformedToken_returns401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/orders").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelOrder_withValidToken_reachesController() throws Exception {
        when(orderService.cancelOrder(1L)).thenReturn(new OrderResponse(1L, "Order cancelled"));

        mockMvc.perform(put("/orders/1/cancel").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void cancelOrder_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/orders/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOrder_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"x\",\"fromAddress\":\"a\",\"toAddress\":\"b\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSingleOrderById_withoutToken_nowRequiresAuthentication() throws Exception {

        mockMvc.perform(get("/orders/42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrderStatus_withoutToken_staysPublic() throws Exception {

        when(orderService.getOrderByIdInternal(42L)).thenReturn(new com.example.orderservice.dto.OrderStatusView());

        mockMvc.perform(get("/orders/42/status"))
                .andExpect(status().isOk());
    }

    @Test
    void searchOrders_withoutToken_returns401() throws Exception {

        mockMvc.perform(get("/orders/search"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignOrder_withCustomerRole_returns403() throws Exception {
        mockMvc.perform(put("/orders/1/assign").param("courierId", "5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignOrder_withAdminRole_reachesController() throws Exception {
        when(orderService.assignOrder(1L, 5L)).thenReturn(new OrderResponse(1L, "Order assigned"));

        mockMvc.perform(put("/orders/1/assign").param("courierId", "5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void createOrder_withCourierRole_returns403() throws Exception {

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"fromAddress\":\"a\",\"toAddress\":\"b\",\"customerPhone\":\"+37411100000\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_withCustomerRole_reachesController() throws Exception {
        when(orderService.createOrder(any())).thenReturn(new OrderResponse(1L, "Order created"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"fromAddress\":\"a\",\"toAddress\":\"b\",\"customerPhone\":\"+37411100000\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isCreated());
    }

    @Test
    void createOrder_withAdminRole_reachesController() throws Exception {
        when(orderService.createOrder(any())).thenReturn(new OrderResponse(1L, "Order created"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"fromAddress\":\"a\",\"toAddress\":\"b\",\"customerPhone\":\"+37411100000\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated());
    }
}