package com.example.authservice.security;

import com.example.authservice.entity.Role;
import com.example.authservice.entity.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtDecoder jwtDecoder;
    private JWKSet jwkSet;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("test-key").build();
        jwkSet = new JWKSet(rsaKey);
        JWKSource<SecurityContext> jwkSource = (selector, context) -> selector.select(jwkSet);

        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        jwtService = new JwtService(jwtEncoder, jwkSet);
        ReflectionTestUtils.setField(jwtService, "issuer", "http://auth-service");
        ReflectionTestUtils.setField(jwtService, "accessTokenTtlSeconds", 900L);
    }

    @Test
    void generateAccessToken_includesExpectedClaims() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("john@example.com");
        user.setRole(Role.ROLE_COURIER);

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getIssuer().toString()).isEqualTo("http://auth-service");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("john@example.com");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ROLE_COURIER");
        assertThat(decoded.getId()).isNotBlank();
        assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
    }

    @Test
    void getAccessTokenTtlSeconds_returnsConfiguredValue() {
        assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(900L);
    }

    @Test
    void getPublicJwkSet_returnsPublicJwksJsonWithoutPrivateKeyMaterial() {
        java.util.Map<String, Object> result = jwtService.getPublicJwkSet();

        assertThat(result).containsKey("keys");
        assertThat(jwkSet.toPublicJWKSet().toJSONObject()).isEqualTo(result);
        assertThat(result.toString()).doesNotContain("\"d\"");
    }
}
