package com.example.authservice.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Issues and validates RS256 JWTs. Auth Service holds the private key; every other
 * service only ever needs the public key, fetched from {@code /auth/.well-known/jwks.json}.
 */
@Slf4j
@Configuration
public class JwtKeyConfig {

    @Value("${jwt.private-key-location:}")
    private String privateKeyLocation;

    @Value("${jwt.public-key-location:}")
    private String publicKeyLocation;

    @Bean
    public KeyPair jwtKeyPair() throws Exception {
        if (StringUtils.hasText(privateKeyLocation) && StringUtils.hasText(publicKeyLocation)) {
            return loadKeyPair(privateKeyLocation, publicKeyLocation);
        }
        log.warn("jwt.private-key-location/jwt.public-key-location not set — generating an ephemeral " +
                "in-memory RSA keypair for this run. Tokens will not survive a restart and no other " +
                "instance will trust them. Configure persistent keys before deploying beyond local dev.");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean
    public RSAPublicKey rsaPublicKey(KeyPair jwtKeyPair) {
        return (RSAPublicKey) jwtKeyPair.getPublic();
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey(KeyPair jwtKeyPair) {
        return (RSAPrivateKey) jwtKeyPair.getPrivate();
    }

    /**
     * kid is derived from the key material itself (RFC 7638 JWK thumbprint) rather than a
     * fixed string. Consumers of the JWKS endpoint (gateway, order-service, ...) cache keys
     * by kid and only re-fetch when they see an unrecognized one; a hardcoded kid meant a
     * restart that rotated the ephemeral keypair (see jwtKeyPair() above) kept the same kid,
     * so already-running consumers kept validating new tokens against the old, now-wrong
     * public key until their cache happened to expire - rejecting valid tokens as invalid
     * for several minutes after every auth-service restart.
     */
    @Bean
    public JWKSet jwkSet(RSAPublicKey publicKey, RSAPrivateKey privateKey) throws Exception {
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyIDFromThumbprint()
                .build();
        return new JWKSet(rsaKey);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(JWKSet jwkSet) {
        return (jwkSelector, context) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    private KeyPair loadKeyPair(String privateKeyPath, String publicKeyPath) throws Exception {
        return new KeyPair(readPublicKey(publicKeyPath), readPrivateKey(privateKeyPath));
    }

    private RSAPrivateKey readPrivateKey(String path) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripPemHeaders(Files.readString(Path.of(path))));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private RSAPublicKey readPublicKey(String path) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripPemHeaders(Files.readString(Path.of(path))));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private String stripPemHeaders(String pem) {
        return pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
    }
}
