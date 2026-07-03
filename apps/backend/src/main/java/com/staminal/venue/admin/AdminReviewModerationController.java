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
@RequestMapping("/v1/admin/reviews")
public class AdminReviewModerationController {

    private final AdminReviewModerationService adminReviewModerationService;

    @GetMapping
    public AdminReviewListResponse getReviews(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return adminReviewModerationService.getReviews(status, page, size);
    }

    @PatchMapping("/{reviewId}/moderation")
    public AdminReviewResponse moderateReview(
            @PathVariable String reviewId,
            @RequestBody AdminReviewModerationRequest request,
            Authentication authentication) {
        return adminReviewModerationService.moderateReview(reviewId, request, authentication);
    }
}
