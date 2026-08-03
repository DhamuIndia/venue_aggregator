package com.staminal.venue.admin;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.admin.AdminUserController.CreateAdminUserRequest;
import com.staminal.venue.admin.AdminUserController.AdminUserListResponse;
import com.staminal.venue.admin.AdminUserController.AdminUserResponse;
import com.staminal.venue.admin.AdminUserController.ResetUserPasswordRequest;
import com.staminal.venue.admin.AdminUserController.UpdateUserRoleRequest;
import com.staminal.venue.admin.AdminUserController.UpdateUserStatusRequest;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.RoleRepository;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminUserService {

    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final Set<UserRole> MANAGED_ADMIN_ROLES = EnumSet.of(UserRole.ADMIN, UserRole.SUPER_ADMIN);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final VendorRepository vendorRepository;
    private final HallRepository hallRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public AdminUserListResponse getUsers(String q, String role, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        UserRole roleFilter = parseRoleFilter(role);
        String statusFilter = normalizeOptional(status);

        List<AdminUserResponse> filtered = userRepository.findAll()
                .stream()
                .filter(user -> matchesText(user, q))
                .filter(user -> roleFilter == null || hasRole(user, roleFilter))
                .filter(user -> statusFilter == null || statusFilter.equalsIgnoreCase(user.getStatus()))
                .sorted(Comparator
                        .comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(User::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);

        return new AdminUserListResponse(
                filtered.subList(fromIndex, toIndex),
                safePage,
                safeSize,
                filtered.size(),
                totalPages);
    }

    public AdminUserResponse createAdminUser(
            CreateAdminUserRequest request,
            Authentication authentication) {
        User actor = currentSuperAdmin(authentication);
        AdminUserInput input = validateCreateRequest(request);

        if (userRepository.existsByPhone(input.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already registered");
        }

        if (userRepository.existsByEmail(input.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        Role role = findRole(input.role());
        String passwordHash = passwordEncoder.encode(input.password());

        User user = new User();
        user.setFullName(input.fullName());
        user.setPhone(input.phone());
        user.setEmail(input.email());
        user.setPasswordHash(passwordHash);
        user.setStatus(ACTIVE);
        user.getRoles().add(role);

        User savedUser = userRepository.save(user);
        syncAdminProfile(savedUser, passwordHash);
        auditAdminUserCreated(actor, savedUser);

        return toResponse(savedUser);
    }

    public AdminUserResponse updateUserStatus(
            String userId,
            UpdateUserStatusRequest request,
            Authentication authentication) {
        User actor = currentAdmin(authentication);
        User target = findUser(userId);
        String nextStatus = normalizeRequiredStatus(request);

        if (actor.getId().equals(target.getId()) && SUSPENDED.equals(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admins cannot suspend themselves");
        }

        if (isManagedAdmin(target) && !hasRole(actor, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a super admin can update admin accounts");
        }

        String previousStatus = normalizeStatusForResponse(target.getStatus());
        if (previousStatus.equals(nextStatus)) {
            return toResponse(target);
        }

        target.setStatus(nextStatus);
        User savedTarget = userRepository.save(target);
        vendorRepository.findByUserId(savedTarget.getId())
                .ifPresent(vendor -> {
                    if (ACTIVE.equals(nextStatus)) {
                        vendor.setStatus(VendorStatus.APPROVED);
                    } else if (SUSPENDED.equals(nextStatus)) {
                        vendor.setStatus(VendorStatus.SUSPENDED);
                    }
                    vendorRepository.save(vendor);
                });

        hallRepository.findByOwnerUserId_Id(savedTarget.getId())
                .forEach(hall -> {
                    if (ACTIVE.equals(nextStatus)) {
                        hall.setStatus(HallStatus.APPROVED);
                    } else if (SUSPENDED.equals(nextStatus)) {
                        hall.setStatus(HallStatus.SUSPENDED);
                    }
                });
        if (isManagedAdmin(savedTarget)) {
            syncAdminProfile(savedTarget, savedTarget.getPasswordHash());
        }
        auditUserStatusChange(actor, savedTarget, previousStatus, nextStatus);

        return toResponse(savedTarget);
    }

    public AdminUserResponse updateUserRole(
            String userId,
            UpdateUserRoleRequest request,
            Authentication authentication) {
        User actor = currentSuperAdmin(authentication);
        User target = findUser(userId);
        UserRole previousRole = primaryRole(target);
        UserRole nextRole = parseManagedRole(request == null ? null : request.role());

        if (actor.getId().equals(target.getId()) && nextRole != UserRole.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Super admins cannot remove their own super admin role");
        }

        ensureAdminEmail(target);

        Role persistedRole = findRole(nextRole);
        target.getRoles().clear();
        target.getRoles().add(persistedRole);

        User savedTarget = userRepository.save(target);
        syncAdminProfile(savedTarget, savedTarget.getPasswordHash());
        auditAdminUserRoleChange(actor, savedTarget, previousRole, nextRole);

        return toResponse(savedTarget);
    }

    public AdminUserResponse resetUserPassword(
            String userId,
            ResetUserPasswordRequest request,
            Authentication authentication) {
        User actor = currentSuperAdmin(authentication);
        User target = findUser(userId);

        if (!isManagedAdmin(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password reset is available only for admin accounts");
        }

        String password = validatePassword(request == null ? null : request.password());
        String passwordHash = passwordEncoder.encode(password);
        target.setPasswordHash(passwordHash);

        User savedTarget = userRepository.save(target);
        syncAdminProfile(savedTarget, passwordHash);
        auditAdminPasswordReset(actor, savedTarget);

        return toResponse(savedTarget);
    }

    private User currentAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = findUser(authentication.getName());
        if (!hasRole(user, UserRole.ADMIN) && !hasRole(user, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
        }
        if (!ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin account is not active");
        }
        return user;
    }

    private User currentSuperAdmin(Authentication authentication) {
        User user = currentAdmin(authentication);
        if (!hasRole(user, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin role is required");
        }
        return user;
    }

    private User findUser(String userId) {
        try {
            return userRepository.findById(Long.valueOf(userId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id", exception);
        }
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                String.valueOf(user.getId()),
                user.getFullName(),
                user.getPhone(),
                user.getEmail(),
                primaryRole(user).name(),
                normalizeStatusForResponse(user.getStatus()),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private boolean matchesText(User user, String q) {
        String needle = normalizeOptional(q);
        if (needle == null) {
            return true;
        }

        String haystack = String.join(" ",
                safe(user.getFullName()),
                safe(user.getPhone()),
                safe(user.getEmail())).toUpperCase(Locale.ROOT);
        return haystack.contains(needle);
    }

    private UserRole parseRoleFilter(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role filter", exception);
        }
    }

    private AdminUserInput validateCreateRequest(CreateAdminUserRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User details are required");
        }

        return new AdminUserInput(
                normalizeFullName(request.fullName()),
                normalizePhone(request.phone()),
                normalizeRequiredEmail(request.email()),
                validatePassword(request.password()),
                parseManagedRole(request.role()));
    }

    private String normalizeRequiredStatus(UpdateUserStatusRequest request) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }

        String normalized = request.status().trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(normalized) && !SUSPENDED.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or SUSPENDED");
        }
        return normalized;
    }

    private UserRole parseManagedRole(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }

        try {
            UserRole role = UserRole.valueOf(normalized);
            if (!MANAGED_ADMIN_ROLES.contains(role)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be ADMIN or SUPER_ADMIN");
            }
            return role;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be ADMIN or SUPER_ADMIN", exception);
        }
    }

    private Role findRole(UserRole role) {
        return roleRepository.findByName(role)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Role is not configured"));
    }

    private String normalizeFullName(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name must be 160 characters or less");
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is required");
        }

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

    private String normalizeRequiredEmail(String value) {
        String normalized = normalizeEmail(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required for admin users");
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        }
        if (normalized.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be 255 characters or less");
        }
        return normalized;
    }

    private String validatePassword(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (value.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        if (value.length() > MAX_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 72 characters or less");
        }
        return value;
    }

    private String normalizeStatusForResponse(String value) {
        return value == null || value.isBlank() ? ACTIVE : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasRole(User user, UserRole role) {
        return user.getRoles() != null && user.getRoles()
                .stream()
                .map(Role::getName)
                .anyMatch(role::equals);
    }

    private boolean isManagedAdmin(User user) {
        return MANAGED_ADMIN_ROLES.stream().anyMatch(role -> hasRole(user, role));
    }

    private UserRole primaryRole(User user) {
        return user.getRoles() == null ? UserRole.CUSTOMER
                : user.getRoles()
                        .stream()
                        .map(Role::getName)
                        .max(Comparator.comparingInt(UserRole::ordinal))
                        .orElse(UserRole.CUSTOMER);
    }

    private void ensureAdminEmail(User user) {
        normalizeRequiredEmail(user.getEmail());
    }

    private void syncAdminProfile(User user, String passwordHash) {
        String email = normalizeRequiredEmail(user.getEmail());
        Instant now = Instant.now();
        Admin admin = adminRepository.findByEmail(email).orElseGet(Admin::new);

        admin.setFullName(user.getFullName());
        admin.setEmail(email);
        admin.setContactNumber(user.getPhone());
        admin.setPasswordHash(passwordHash);
        admin.setStatus(normalizeStatusForResponse(user.getStatus()));
        if (admin.getCreatedAt() == null) {
            admin.setCreatedAt(now);
        }
        admin.setUpdatedAt(now);

        adminRepository.save(admin);
    }

    private Map<String, Object> targetMetadata(User target) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetPhone", target.getPhone());
        metadata.put("targetEmail", target.getEmail());
        metadata.put("targetRole", primaryRole(target).name());
        return metadata;
    }

    private void auditAdminUserCreated(User actor, User target) {
        auditService.record(new AuditCommand(
                actor.getId(),
                primaryRole(actor).name(),
                AuditAction.ADMIN_USER_CREATED,
                "USER",
                String.valueOf(target.getId()),
                "Super admin created admin user",
                Map.of(),
                Map.of(
                        "role", primaryRole(target).name(),
                        "status", normalizeStatusForResponse(target.getStatus())),
                targetMetadata(target)));
    }

    private void auditAdminUserRoleChange(User actor, User target, UserRole previousRole, UserRole nextRole) {
        auditService.record(new AuditCommand(
                actor.getId(),
                primaryRole(actor).name(),
                AuditAction.ADMIN_USER_ROLE_CHANGED,
                "USER",
                String.valueOf(target.getId()),
                "Super admin changed user role",
                Map.of("role", previousRole.name()),
                Map.of("role", nextRole.name()),
                targetMetadata(target)));
    }

    private void auditAdminPasswordReset(User actor, User target) {
        auditService.record(new AuditCommand(
                actor.getId(),
                primaryRole(actor).name(),
                AuditAction.ADMIN_USER_PASSWORD_RESET,
                "USER",
                String.valueOf(target.getId()),
                "Super admin reset admin user password",
                Map.of(),
                Map.of("passwordUpdated", true),
                targetMetadata(target)));
    }

    private void auditUserStatusChange(User actor, User target, String previousStatus, String nextStatus) {
        auditService.record(new AuditCommand(
                actor.getId(),
                primaryRole(actor).name(),
                AuditAction.ADMIN_USER_STATUS_CHANGED,
                "USER",
                String.valueOf(target.getId()),
                "Admin changed user status",
                Map.of("status", previousStatus),
                Map.of("status", nextStatus),
                targetMetadata(target)));
    }

    private record AdminUserInput(
            String fullName,
            String phone,
            String email,
            String password,
            UserRole role) {
    }
}
