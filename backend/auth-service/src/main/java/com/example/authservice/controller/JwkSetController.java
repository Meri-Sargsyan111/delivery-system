package com.example.authservice.controller;

import com.example.authservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the public half of the signing key so the gateway and every downstream
 * service can validate JWTs locally without ever talking to Auth Service at request time.
 */
@RestController
@RequiredArgsConstructor
public class JwkSetController {

    private final JwtService jwtService;

    @GetMapping("/auth/.well-known/jwks.json")
    public Map<String, Object> getJwkSet() {
        return jwtService.getPublicJwkSet();
    }
}
