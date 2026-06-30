package com.staminal.venue.customer;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.reviews.ReviewService;
import com.staminal.venue.reviews.dto.ReviewEligibilityResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/customer")
@RequiredArgsConstructor
public class CustomerReviewController {

    private final ReviewService reviewService;

    @GetMapping("/review-eligibility")
    public ReviewEligibilityResponse checkEligibility(
            @RequestParam Long enquiryId,
            Authentication authentication) {

        return reviewService.checkEligibility(enquiryId, authentication);
    }
}