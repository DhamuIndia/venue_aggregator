package com.staminal.venue.auth.dto;

import com.staminal.venue.enums.UserRole;

public record AuthUserResponse(
        String id,
        String fullName,
        String phone,
        String email,
        UserRole role,
        String status) {
}
