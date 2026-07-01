package com.staminal.venue.admin;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public AdminUserListResponse getUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return adminUserService.getUsers(q, role, status, page, size);
    }

    @PatchMapping("/{userId}/status")
    public AdminUserResponse updateUserStatus(
            @PathVariable String userId,
            @RequestBody UpdateUserStatusRequest request,
            Authentication authentication) {
        return adminUserService.updateUserStatus(userId, request, authentication);
    }

    public record UpdateUserStatusRequest(String status) {
    }

    public record AdminUserListResponse(
            List<AdminUserResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record AdminUserResponse(
            String id,
            String fullName,
            String phone,
            String email,
            String role,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }
}
