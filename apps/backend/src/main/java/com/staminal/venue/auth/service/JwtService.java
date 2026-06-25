package com.staminal.venue.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms:900000}") long accessExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(Long userId, String role) {
        return buildToken(userId.toString(), role, ACCESS_TOKEN, accessExpirationMs);
    }

    public String generateRefreshToken(Long userId, String role) {
        return buildToken(userId.toString(), role, REFRESH_TOKEN, refreshExpirationMs);
    }

    // Retained while the existing vendor and admin login modules migrate to unified users.
    public String generateToken(String identity, String role) {
        return buildToken(identity, role, ACCESS_TOKEN, accessExpirationMs);
    }

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractEmail(String token) {
        return extractSubject(token);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN.equals(extractAllClaims(token).get("tokenType", String.class));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN.equals(extractAllClaims(token).get("tokenType", String.class));
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }

    private String buildToken(String subject, String role, String tokenType, long expirationMs) {
        Date issuedAt = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("tokenType", tokenType)
                .issuedAt(issuedAt)
                .expiration(new Date(issuedAt.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
