package com.staminal.venue.reviews.VendorReview;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;

import com.staminal.venue.vendors.Dto.VendorReviewListResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class VendorReviewController {

    private final VendorReviewService vendorReviewService;

    @GetMapping("/vendor/reviews")
    public VendorReviewListResponse getMyReviews(Authentication authentication) {
        return vendorReviewService.getMyReviews(authentication);
    }

    @PostMapping("/customer/vendor-reviews")
    public VendorReviewResponse createVendorReview(
            @Valid @RequestBody CreateVendorReviewRequest request,
            Authentication authentication) {

        return vendorReviewService.createVendorReview(request, authentication);
    }

    @GetMapping("/customer/vendor-review-eligibility")
    public VendorReviewEligibilityResponse getEligibility(
            @RequestParam String leadId,
            Authentication authentication) {

        return vendorReviewService.getReviewEligibility(leadId, authentication);
    }
}
