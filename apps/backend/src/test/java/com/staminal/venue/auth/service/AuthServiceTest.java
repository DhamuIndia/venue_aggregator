package com.staminal.venue.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.auth.dto.AuthResponse;
import com.staminal.venue.auth.dto.LoginRequest;
import com.staminal.venue.auth.dto.RegisterRequest;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.RoleRepository;
import com.staminal.venue.users.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCustomerHashesPasswordAndIssuesSession() {
        RegisterRequest request = new RegisterRequest(
                " Arun Kumar ",
                "+91 98765 43210",
                " Arun@Example.com ",
                "secret123",
                UserRole.HALL_OWNER);

        when(roleRepository.findByName(UserRole.HALL_OWNER)).thenReturn(Optional.of(role(UserRole.HALL_OWNER)));
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(jwtService.generateAccessToken(42L, "HALL_OWNER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken(42L, "HALL_OWNER")).thenReturn("refresh-token");
        when(jwtService.getAccessExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).existsByPhone("9876543210");
        verify(userRepository).existsByEmail("arun@example.com");
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getFullName()).isEqualTo("Arun Kumar");
        assertThat(savedUser.getPhone()).isEqualTo("9876543210");
        assertThat(savedUser.getEmail()).isEqualTo("arun@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().role()).isEqualTo(UserRole.HALL_OWNER);
    }

    @Test
    void registerRejectsAdminRole() {
        RegisterRequest request = new RegisterRequest(
                "Admin User",
                "9876543210",
                "admin@example.com",
                "secret123",
                UserRole.ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("This role cannot be self-registered");

        verifyNoInteractions(roleRepository, passwordEncoder, jwtService);
    }

    @Test
    void loginRejectsBadPassword() {
        User user = user(UserRole.CUSTOMER);
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("9876543210", "wrongpass")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid phone or password");

        verifyNoInteractions(jwtService);
    }

    private User user(UserRole userRole) {
        User user = new User();
        user.setId(42L);
        user.setFullName("Priya Raman");
        user.setPhone("9876543210");
        user.setEmail("priya@example.com");
        user.setPasswordHash("hashed-password");
        user.setStatus("ACTIVE");
        user.getRoles().add(role(userRole));
        return user;
    }

    private Role role(UserRole userRole) {
        Role role = new Role();
        role.setId(1L);
        role.setName(userRole);
        return role;
    }
}
