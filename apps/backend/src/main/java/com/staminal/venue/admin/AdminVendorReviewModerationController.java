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
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminVendorReviewModerationController {

    private final AdminVendorReviewModerationService service;

    @GetMapping("/vendor-reviews")
    public AdminVendorReviewListResponse getReviews(

            @RequestParam(required = false) String status,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size) {
        // System.out.println("Authentication = " + authentication);

        return service.getReviews(status, page, size);
    }

    @PatchMapping("/vendor-reviews/{id}")
    public AdminVendorReviewResponse moderateReview(

            @PathVariable String id,

            @RequestBody AdminReviewModerationRequest request,

            Authentication authentication) {

        return service.moderateReview(id, request, authentication);
    }

}