package com.staminal.venue.discovery;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.admin.AdminRepository;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VenueDiscoveryAccess {
    private final UserRepository users;
    private final AdminRepository admins;

    public record Actor(Long userId, String role, Admin admin) { }

    @Transactional(readOnly = true)
    public Actor requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin session");
        }
        User user = users.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin session"));
        boolean adminRole = user.getRoles() != null && user.getRoles().stream().anyMatch(role ->
                role.getName() == UserRole.ADMIN || role.getName() == UserRole.SUPER_ADMIN);
        if (!adminRole || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active admin role required");
        }
        Admin admin = user.getEmail() == null ? null : admins.findByEmail(user.getEmail()).orElse(null);
        if (admin == null || !"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active admin profile required");
        }
        String role = user.getRoles().stream().anyMatch(r -> r.getName() == UserRole.SUPER_ADMIN)
                ? "SUPER_ADMIN" : "ADMIN";
        return new Actor(user.getId(), role, admin);
    }
}
