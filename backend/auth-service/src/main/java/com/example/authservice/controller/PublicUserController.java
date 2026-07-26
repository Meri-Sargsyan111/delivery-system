package com.example.authservice.controller;

import com.example.authservice.dto.BatchUserContactRequest;
import com.example.authservice.dto.UserContactResponse;
import com.example.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Cross-service contact lookup for ANY role (customer or courier), unlike
 * CustomerController's /customers/{id} which only ever resolves a ROLE_CUSTOMER user.
 * Needed by courier-service's live-tracking contact card (see courier-service's
 * AuthServiceClient) to show a courier's name/phone without duplicating that data
 * locally. Deliberately public (see SecurityConfig's PUBLIC_PATHS) - called synchronously
 * over plain RestTemplate with no bearer token, same pre-existing service-to-service-auth
 * gap as /customers/{id} and courier-service's own reserve endpoint.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class PublicUserController {

    private final AuthService authService;

    @GetMapping("/{id}/contact")
    public ResponseEntity<UserContactResponse> getContact(@PathVariable UUID id) {
        log.info("GET /users/{}/contact - resolving contact info", id);
        return ResponseEntity.ok(authService.getUserContact(id));
    }

    /**
     * Batch counterpart of {@link #getContact} - lets a caller resolve an entire roster
     * (e.g. courier-service's courier list) in one round trip instead of one call per id.
     * Unknown/disabled ids are silently omitted from the response rather than failing the
     * whole batch, same not-found semantics as the single-id endpoint.
     */
    @PostMapping("/contacts/batch")
    public ResponseEntity<List<UserContactResponse>> getContacts(@RequestBody BatchUserContactRequest request) {
        log.info("POST /users/contacts/batch - resolving {} contact(s)",
                request.ids() != null ? request.ids().size() : 0);
        return ResponseEntity.ok(authService.getUserContacts(request.ids()));
    }
}