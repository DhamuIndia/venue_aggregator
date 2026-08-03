package com.staminal.venue.admin;

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
@RequestMapping("/v1/admin/halls")
public class AdminHallModerationController {

    private final AdminHallModerationService adminHallModerationService;

    @GetMapping
    public AdminHallListResponse getHalls(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return adminHallModerationService.getHalls(status, page, size);
    }

    @PatchMapping("/{hallId}/review")
    public AdminHallResponse reviewHall(
            @PathVariable String hallId,
            @RequestBody AdminReviewRequest request,
            Authentication authentication) {
        return adminHallModerationService.reviewHall(hallId, request, authentication);
    }
}
