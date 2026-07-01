package com.staminal.venue.reviews;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.reviews.Dto.CreateReviewRequest;
import com.staminal.venue.reviews.Dto.ReviewEligibilityResponse;
import com.staminal.venue.reviews.Dto.ReviewResponse;
import com.staminal.venue.reviews.Dto.UpdateReviewRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/customer")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/review-eligibility")
    public ReviewEligibilityResponse getEligibility(
            @RequestParam Long enquiryId,
            Authentication authentication) {

        System.out.println("Authentication = " + authentication);

        return reviewService.getEligibility(enquiryId, authentication);
    }

    @PostMapping("/reviews")
    public ReviewResponse createReview(
            @Valid @RequestBody CreateReviewRequest request,
            Authentication authentication) {

        return reviewService.createReview(request, authentication);
    }

    @PutMapping("/reviews/{reviewId}")
    public ReviewResponse updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateReviewRequest request,
            Authentication authentication) {

        return reviewService.updateReview(reviewId, request, authentication);
    }

}