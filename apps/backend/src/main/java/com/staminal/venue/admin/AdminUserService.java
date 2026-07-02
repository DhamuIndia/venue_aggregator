package com.staminal.venue.admin;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.admin.AdminUserController.AdminUserListResponse;
import com.staminal.venue.admin.AdminUserController.AdminUserResponse;
import com.staminal.venue.admin.AdminUserController.UpdateUserStatusRequest;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminUserService {

    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";

    private final UserRepository userRepository;
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

        if (hasRole(target, UserRole.SUPER_ADMIN) && !hasRole(actor, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a super admin can update a super admin");
        }

        String previousStatus = normalizeStatusForResponse(target.getStatus());
        if (previousStatus.equals(nextStatus)) {
            return toResponse(target);
        }

        target.setStatus(nextStatus);
        User savedTarget = userRepository.save(target);
        auditUserStatusChange(actor, savedTarget, previousStatus, nextStatus);

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

    private UserRole primaryRole(User user) {
        return user.getRoles() == null ? UserRole.CUSTOMER : user.getRoles()
                .stream()
                .map(Role::getName)
                .max(Comparator.comparingInt(UserRole::ordinal))
                .orElse(UserRole.CUSTOMER);
    }

    private void auditUserStatusChange(User actor, User target, String previousStatus, String nextStatus) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetPhone", target.getPhone());
        metadata.put("targetEmail", target.getEmail());
        metadata.put("targetRole", primaryRole(target).name());

        auditService.record(new AuditCommand(
                actor.getId(),
                primaryRole(actor).name(),
                AuditAction.ADMIN_USER_STATUS_CHANGED,
                "USER",
                String.valueOf(target.getId()),
                "Admin changed user status",
                Map.of("status", previousStatus),
                Map.of("status", nextStatus),
                metadata));
    }
}
