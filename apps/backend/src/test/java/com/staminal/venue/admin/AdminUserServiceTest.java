package com.staminal.venue.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.admin.AdminUserController.CreateAdminUserRequest;
import com.staminal.venue.admin.AdminUserController.ResetUserPasswordRequest;
import com.staminal.venue.admin.AdminUserController.UpdateUserRoleRequest;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.RoleRepository;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private AuditService auditService;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                roleRepository,
                adminRepository,
                passwordEncoder,
                vendorRepository,
                hallRepository,
                auditService);
    }

    @Test
    void superAdminCreatesAdminUserAndLegacyAdminRecord() {
        User actor = user(1L, UserRole.SUPER_ADMIN, "9000000001", "super@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(roleRepository.findByName(UserRole.ADMIN)).thenReturn(Optional.of(role(UserRole.ADMIN)));
        when(passwordEncoder.encode("Password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(20L);
            return user;
        });
        when(adminRepository.findByEmail("ops@example.com")).thenReturn(Optional.empty());

        var response = adminUserService.createAdminUser(
                new CreateAdminUserRequest(
                        " Ops Admin ",
                        "+91 90000 00002",
                        " Ops@Example.com ",
                        "Password123",
                        "ADMIN"),
                auth(1L, UserRole.SUPER_ADMIN));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Admin> adminCaptor = ArgumentCaptor.forClass(Admin.class);
        verify(userRepository).save(userCaptor.capture());
        verify(adminRepository).save(adminCaptor.capture());
        verify(auditService).record(any());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getFullName()).isEqualTo("Ops Admin");
        assertThat(savedUser.getPhone()).isEqualTo("9000000002");
        assertThat(savedUser.getEmail()).isEqualTo("ops@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(response.role()).isEqualTo("ADMIN");

        Admin legacyAdmin = adminCaptor.getValue();
        assertThat(legacyAdmin.getEmail()).isEqualTo("ops@example.com");
        assertThat(legacyAdmin.getContactNumber()).isEqualTo("9000000002");
        assertThat(legacyAdmin.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(legacyAdmin.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void normalAdminCannotCreateAdminUser() {
        User actor = user(1L, UserRole.ADMIN, "9000000001", "admin@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> adminUserService.createAdminUser(
                new CreateAdminUserRequest(
                        "Ops Admin",
                        "9000000002",
                        "ops@example.com",
                        "Password123",
                        "ADMIN"),
                auth(1L, UserRole.ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verifyNoInteractions(roleRepository, passwordEncoder, adminRepository, auditService);
    }

    @Test
    void superAdminCanPromoteExistingAdmin() {
        User actor = user(1L, UserRole.SUPER_ADMIN, "9000000001", "super@example.com");
        User target = user(20L, UserRole.ADMIN, "9000000002", "ops@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName(UserRole.SUPER_ADMIN)).thenReturn(Optional.of(role(UserRole.SUPER_ADMIN)));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adminRepository.findByEmail("ops@example.com")).thenReturn(Optional.empty());

        var response = adminUserService.updateUserRole(
                "20",
                new UpdateUserRoleRequest("SUPER_ADMIN"),
                auth(1L, UserRole.SUPER_ADMIN));

        assertThat(response.role()).isEqualTo("SUPER_ADMIN");
        assertThat(target.getRoles()).extracting(Role::getName).containsExactly(UserRole.SUPER_ADMIN);
        verify(adminRepository).save(any(Admin.class));
        verify(auditService).record(any());
    }

    @Test
    void superAdminCannotDemoteSelf() {
        User actor = user(1L, UserRole.SUPER_ADMIN, "9000000001", "super@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> adminUserService.updateUserRole(
                "1",
                new UpdateUserRoleRequest("ADMIN"),
                auth(1L, UserRole.SUPER_ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        verifyNoInteractions(roleRepository, adminRepository, auditService);
    }

    @Test
    void superAdminResetsAdminPasswordInUsersAndLegacyAdmin() {
        User actor = user(1L, UserRole.SUPER_ADMIN, "9000000001", "super@example.com");
        User target = user(20L, UserRole.ADMIN, "9000000002", "ops@example.com");
        Admin legacyAdmin = new Admin();

        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adminRepository.findByEmail("ops@example.com")).thenReturn(Optional.of(legacyAdmin));

        adminUserService.resetUserPassword(
                "20",
                new ResetUserPasswordRequest("NewPass123"),
                auth(1L, UserRole.SUPER_ADMIN));

        assertThat(target.getPasswordHash()).isEqualTo("hashed-new-password");
        assertThat(legacyAdmin.getPasswordHash()).isEqualTo("hashed-new-password");
        verify(adminRepository).save(legacyAdmin);
        verify(auditService).record(any());
    }

    private UsernamePasswordAuthenticationToken auth(Long userId, UserRole userRole) {
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + userRole.name())));
    }

    private User user(Long id, UserRole userRole, String phone, String email) {
        User user = new User();
        user.setId(id);
        user.setFullName(userRole.name() + " User");
        user.setPhone(phone);
        user.setEmail(email);
        user.setPasswordHash("existing-hash");
        user.setStatus("ACTIVE");
        user.getRoles().add(role(userRole));
        return user;
    }

    private Role role(UserRole userRole) {
        Role role = new Role();
        role.setId((long) userRole.ordinal() + 1);
        role.setName(userRole);
        return role;
    }
}
