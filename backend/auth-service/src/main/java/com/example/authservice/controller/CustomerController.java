package com.example.authservice.controller;

import com.example.authservice.dto.CustomerSummaryResponse;
import com.example.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Mapped at the service root (/customers), not under /auth, to match the gateway's
 * one-route-per-path-prefix convention (see api-gateway's application.yml and the
 * Caddyfile's mirrored path list) - every other cross-service-visible resource
 * (/orders, /courier, ...) is routed the same way.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/customers")
public class CustomerController {

    private final AuthService authService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<CustomerSummaryResponse>> listCustomers() {
        log.info("GET /customers - listing customers");
        return ResponseEntity.ok(authService.listCustomers());
    }

    /**
     * Deliberately public (see SecurityConfig's PUBLIC_PATHS) - order-service's
     * AuthServiceClient calls this synchronously, over plain RestTemplate with no
     * bearer token, to resolve the customer an admin picked when creating an order.
     * Same pre-existing service-to-service-auth gap as courier-service's reserve endpoint.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomerSummaryResponse> getCustomerById(@PathVariable UUID id) {
        log.info("GET /customers/{} - resolving customer", id);
        return ResponseEntity.ok(authService.getCustomerById(id));
    }
}