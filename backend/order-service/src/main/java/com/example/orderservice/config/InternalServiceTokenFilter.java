package com.example.orderservice.config;

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
 * POST /orders/internal/from-payment (payment-service) and PUT /orders/internal/{id}/unassign
 * (courier-service, see OrderController) are both called synchronously by other services and
 * carry no end-user JWT - they're service-to-service calls with no request-scoped identity to
 * validate. Mirrors courier-service's InternalServiceTokenFilter exactly, reusing the SAME
 * shared secret (internal.service-token) order-service already presents to courier-service's
 * reserve endpoint - one secret for every internal direction, not one per pair of services.
 */
@Slf4j
@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Token";
    private static final String FROM_PAYMENT_PATH = "/orders/internal/from-payment";
    private static final Pattern UNASSIGN_PATH = Pattern.compile("^/orders/internal/\\d+/unassign$");

    private final String expectedToken;

    public InternalServiceTokenFilter(@Value("${internal.service-token}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (!isProtectedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedToken = request.getHeader(HEADER_NAME);
        if (providedToken == null || !providedToken.equals(expectedToken)) {
            log.warn("Rejected internal call to {} - missing/invalid {}", request.getRequestURI(), HEADER_NAME);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Missing or invalid internal service token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedRequest(HttpServletRequest request) {
        if (HttpMethod.POST.matches(request.getMethod()) && FROM_PAYMENT_PATH.equals(request.getRequestURI())) {
            return true;
        }
        return HttpMethod.PUT.matches(request.getMethod()) && UNASSIGN_PATH.matcher(request.getRequestURI()).matches();
    }
}
