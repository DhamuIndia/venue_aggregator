package com.staminal.venue.auth.service;

import java.util.Date;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

        @Value("${jwt.secret}")
        private String secret;

        private SecretKey getKey() {
                return Keys.hmacShaKeyFor(
                                secret.getBytes());
        }

        public String generateToken(String email, String role) {

                return Jwts.builder()
                                .subject(email)
                                .claim("role", role)
                                .issuedAt(new Date())
                                .expiration(
                                                new Date(System.currentTimeMillis()
                                                                + 86400000))
                                .signWith(getKey())
                                .compact();
        }

        public String extractEmail(String token) {

                return Jwts.parser()
                                .verifyWith(getKey())
                                .build()
                                .parseSignedClaims(token)
                                .getPayload()
                                .getSubject();
        }

        private Claims extractAllClaims(String token) {

                return Jwts.parser()
                                .verifyWith(getKey())
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();
        }

        public String extractRole(
                        String token) {

                Claims claims = extractAllClaims(token);

                return claims.get(
                                "role",
                                String.class);
        }
}
