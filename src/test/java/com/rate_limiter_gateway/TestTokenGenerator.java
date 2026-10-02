package com.rate_limiter_gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class TestTokenGenerator {
    public static void main(String[] args) {

        String secret =
                "dev-only-secret-change-this-before-production-9f4a7c2e8b1d6f3a";

        SecretKey key =
                Keys.hmacShaKeyFor(
                        secret.getBytes(StandardCharsets.UTF_8)
                );

        String token =
                Jwts.builder()
                        .subject("test-user")
                        .claim("tenant_id", "tenant-456")
                        .claim("tier", "ENTERPRISE")
                        .issuedAt(new Date())
                        .expiration(
                                new Date(
                                        System.currentTimeMillis()
                                                + 60 * 60 * 1000
                                )
                        )
                        .signWith(key)
                        .compact();

        System.out.println(token);
    }
}
