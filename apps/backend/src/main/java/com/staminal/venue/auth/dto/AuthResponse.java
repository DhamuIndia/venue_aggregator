package com.staminal.venue.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        AuthUserResponse user) {
}
