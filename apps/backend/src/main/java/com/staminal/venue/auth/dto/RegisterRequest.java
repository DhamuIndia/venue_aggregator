package com.staminal.venue.auth.dto;

import com.staminal.venue.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 160, message = "Full name must be at most 160 characters") String fullName,
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[0-9+() -]{10,20}$", message = "Enter a valid phone number") String phone,
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters") String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String password,
        @NotNull(message = "Role is required") UserRole role) {
}
