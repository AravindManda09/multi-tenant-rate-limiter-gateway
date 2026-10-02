package com.rate_limiter_gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "dev-only-secret-change-this-before-production-9f4a7c2e8b1d6f3a";
    private JwtService jwtService;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validToken_returnsClaims() {
        String token = Jwts.builder()
                .subject("test-user")
                .claim("tenant_id", "tenant-123")
                .claim("tier", "FREE")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        var claims = jwtService.validateToken(token);

        assertEquals("test-user", claims.getSubject());
        assertEquals("tenant-123", claims.get("tenant_id", String.class));
        assertEquals("FREE", claims.get("tier", String.class));
    }

    @Test
    void expiredToken_throwsException() {
        String token = Jwts.builder()
                .subject("test-user")
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // expired 1 hour ago
                .signWith(key)
                .compact();

        assertThrows(Exception.class, () -> jwtService.validateToken(token));
    }

    @Test
    void tamperedToken_throwsException() {
        String token = Jwts.builder()
                .subject("test-user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();

        // Flip a character in the signature portion to simulate tampering
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThrows(Exception.class, () -> jwtService.validateToken(tampered));
    }

    @Test
    void wrongSecret_throwsException() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-that-is-long-enough-for-hmac-sha-algorithm-00000".getBytes(StandardCharsets.UTF_8)
        );

        String token = Jwts.builder()
                .subject("test-user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(wrongKey)
                .compact();

        assertThrows(Exception.class, () -> jwtService.validateToken(token));
    }

    @Test
    void nullToken_throwsException() {
        assertThrows(Exception.class, () -> jwtService.validateToken(null));
    }

    @Test
    void emptyToken_throwsException() {
        assertThrows(Exception.class, () -> jwtService.validateToken(""));
    }

    @Test
    void garbageString_throwsException() {
        assertThrows(Exception.class, () -> jwtService.validateToken("not.a.jwt"));
    }
}