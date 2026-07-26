package com.example.courierservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * PUT /courier/{courierId}/reserve/{orderId} is called synchronously by order-service
 * and carries no end-user JWT (it's a service-to-service call, not made on behalf of an
 * authenticated user), so it stays permitAll() in SecurityConfig rather than going
 * through the normal JWT resource-server chain. Left with no check at all, any
 * unauthenticated caller could force-reserve/free arbitrary couriers or forge an
 * orderId. This filter closes that gap with a shared secret both services agree on via
 * env var (INTERNAL_SERVICE_TOKEN), short of a full service-to-service OAuth
 * client-credentials flow, which is out of scope for this pass.
 */
@Slf4j
@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Token";
    private static final Pattern RESERVE_PATH = Pattern.compile("^/courier/\\d+/reserve/\\d+$");

    private final String expectedToken;

    public InternalServiceTokenFilter(@Value("${internal.service-token}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (!isReserveRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedToken = request.getHeader(HEADER_NAME);
        if (providedToken == null || !providedToken.equals(expectedToken)) {
            log.warn("Rejected courier reservation call to {} - missing/invalid {}",
                    request.getRequestURI(), HEADER_NAME);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Missing or invalid internal service token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isReserveRequest(HttpServletRequest request) {
        return HttpMethod.PUT.matches(request.getMethod())
                && RESERVE_PATH.matcher(request.getRequestURI()).matches();
    }
}