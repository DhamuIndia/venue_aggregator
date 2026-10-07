package com.staminal.venue.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.admin.Admin;
import com.staminal.venue.admin.AdminRepository;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class VenueDiscoveryAccessTest {
    @Mock private UserRepository users;
    @Mock private AdminRepository admins;
    private VenueDiscoveryAccess access;

    @BeforeEach
    void setUp() {
        access = new VenueDiscoveryAccess(users, admins);
    }

    @Test
    void missingOrUnauthenticatedSessionIsRejected() {
        assertDenied(null, HttpStatus.UNAUTHORIZED);
        assertDenied(new UsernamePasswordAuthenticationToken("301", "unused"), HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(users, admins);
    }

    @Test
    void invalidSessionSubjectIsRejectedWithoutDatabaseLookup() {
        assertDenied(session("anonymousUser"), HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(users, admins);
    }

    @Test
    void missingUserIsRejected() {
        when(users.findById(301L)).thenReturn(Optional.empty());
        assertDenied(session("301"), HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(admins);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "SUPER_ADMIN"})
    void activeDatabaseAdminAndProfileAreRequiredAndAccepted(UserRole role) {
        User user = databaseUser(role);
        Admin admin = activeAdmin();
        when(users.findById(301L)).thenReturn(Optional.of(user));
        when(admins.findByEmail(user.getEmail())).thenReturn(Optional.of(admin));

        VenueDiscoveryAccess.Actor actor = access.requireAdmin(session("301"));

        assertThat(actor.userId()).isEqualTo(301L);
        assertThat(actor.role()).isEqualTo(role.name());
        assertThat(actor.admin()).isSameAs(admin);
    }

    @Test
    void superAdminTakesPrecedenceWhenBothDatabaseRolesArePresent() {
        User user = databaseUser(UserRole.ADMIN);
        user.setRoles(Set.of(role(UserRole.ADMIN), role(UserRole.SUPER_ADMIN)));
        when(users.findById(301L)).thenReturn(Optional.of(user));
        when(admins.findByEmail(user.getEmail())).thenReturn(Optional.of(activeAdmin()));

        assertThat(access.requireAdmin(session("301")).role()).isEqualTo("SUPER_ADMIN");
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "VENDOR", "HALL_OWNER"})
    void staleAdminTokenCannotOverrideRevokedDatabaseRole(UserRole currentRole) {
        when(users.findById(301L)).thenReturn(Optional.of(databaseUser(currentRole)));
        assertDenied(session("301"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(admins);
    }

    @Test
    void absentDatabaseRolesFailClosed() {
        User user = databaseUser(UserRole.ADMIN);
        user.setRoles(null);
        when(users.findById(301L)).thenReturn(Optional.of(user));
        assertDenied(session("301"), HttpStatus.FORBIDDEN);
        user.setRoles(Set.of());
        assertDenied(session("301"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(admins);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"SUSPENDED", "INACTIVE", "PENDING"})
    void inactiveDatabaseUserIsDeniedDespiteAdminToken(String status) {
        User user = databaseUser(UserRole.ADMIN);
        user.setStatus(status);
        when(users.findById(301L)).thenReturn(Optional.of(user));
        assertDenied(session("301"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(admins);
    }

    @Test
    void activeUserWithoutAnAdminProfileIsDenied() {
        User user = databaseUser(UserRole.ADMIN);
        when(users.findById(301L)).thenReturn(Optional.of(user));
        when(admins.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        assertDenied(session("301"), HttpStatus.FORBIDDEN);
    }

    @Test
    void userWithoutEmailCannotResolveAnAdminProfile() {
        User user = databaseUser(UserRole.ADMIN);
        user.setEmail(null);
        when(users.findById(301L)).thenReturn(Optional.of(user));
        assertDenied(session("301"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(admins);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"SUSPENDED", "INACTIVE", "PENDING"})
    void inactiveAdminProfileIsDenied(String status) {
        User user = databaseUser(UserRole.ADMIN);
        Admin admin = activeAdmin();
        admin.setStatus(status);
        when(users.findById(301L)).thenReturn(Optional.of(user));
        when(admins.findByEmail(user.getEmail())).thenReturn(Optional.of(admin));
        assertDenied(session("301"), HttpStatus.FORBIDDEN);
    }

    private void assertDenied(Authentication authentication, HttpStatus status) {
        assertThatThrownBy(() -> access.requireAdmin(authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    }

    private static Authentication session(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static User databaseUser(UserRole userRole) {
        User user = new User();
        user.setId(301L);
        user.setEmail("admin@example.test");
        user.setStatus("ACTIVE");
        user.setRoles(Set.of(role(userRole)));
        return user;
    }

    private static Role role(UserRole name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private static Admin activeAdmin() {
        Admin admin = new Admin();
        admin.setId(12L);
        admin.setEmail("admin@example.test");
        admin.setStatus("ACTIVE");
        return admin;
    }
}
