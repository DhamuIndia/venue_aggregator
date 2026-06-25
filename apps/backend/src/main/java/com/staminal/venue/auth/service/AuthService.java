package com.staminal.venue.auth.service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.auth.dto.AuthResponse;
import com.staminal.venue.auth.dto.AuthUserResponse;
import com.staminal.venue.auth.dto.LoginRequest;
import com.staminal.venue.auth.dto.RefreshTokenRequest;
import com.staminal.venue.auth.dto.RegisterRequest;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.RoleRepository;
import com.staminal.venue.users.Repository.UserRepository;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private static final Set<UserRole> SELF_REGISTRATION_ROLES = EnumSet.of(
            UserRole.CUSTOMER,
            UserRole.HALL_OWNER,
            UserRole.VENDOR);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        UserRole role = request.role();
        if (!SELF_REGISTRATION_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This role cannot be self-registered");
        }

        String phone = normalizePhone(request.phone());
        String email = normalizeEmail(request.email());

        if (userRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already registered");
        }

        if (email != null && userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        Role persistedRole = roleRepository.findByName(role)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Role is not configured"));

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus("ACTIVE");
        user.getRoles().add(persistedRole);

        return createSession(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String phone = normalizePhone(request.phone());
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BadCredentialsException("Invalid phone or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid phone or password");
        }

        assertActive(user);
        return createSession(user);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(String principalName) {
        User user = findUserFromPrincipal(principalName);
        assertActive(user);
        return toAuthUser(user, primaryRole(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        try {
            if (!jwtService.isRefreshToken(request.refreshToken())) {
                throw new BadCredentialsException("Invalid refresh token");
            }

            User user = findUserFromPrincipal(jwtService.extractSubject(request.refreshToken()));
            assertActive(user);
            return createSession(user);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid refresh token", exception);
        }
    }

    public void logout() {
        // Stateless JWT logout is handled client-side until refresh token storage is added.
    }

    private AuthResponse createSession(User user) {
        UserRole role = primaryRole(user);
        return new AuthResponse(
                jwtService.generateAccessToken(user.getId(), role.name()),
                jwtService.generateRefreshToken(user.getId(), role.name()),
                jwtService.getAccessExpirationSeconds(),
                toAuthUser(user, role));
    }

    private AuthUserResponse toAuthUser(User user, UserRole role) {
        return new AuthUserResponse(
                String.valueOf(user.getId()),
                user.getFullName(),
                user.getPhone(),
                user.getEmail(),
                role,
                user.getStatus());
    }

    private UserRole primaryRole(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .min(Comparator.comparingInt(UserRole::ordinal))
                .orElse(UserRole.CUSTOMER);
    }

    private User findUserFromPrincipal(String principalName) {
        try {
            return userRepository.findById(Long.valueOf(principalName))
                    .orElseThrow(() -> new BadCredentialsException("Invalid session"));
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Invalid session", exception);
        }
    }

    private void assertActive(User user) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("Account is not active");
        }
    }

    private String normalizePhone(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit phone number");
        }
        return digits;
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
