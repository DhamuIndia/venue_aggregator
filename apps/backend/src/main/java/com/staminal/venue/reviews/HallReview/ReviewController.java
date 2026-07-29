package com.staminal.venue.reviews.HallReview;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.reviews.HallReview.Dto.CreateReviewRequest;
import com.staminal.venue.reviews.HallReview.Dto.ReviewEligibilityResponse;
import com.staminal.venue.reviews.HallReview.Dto.ReviewResponse;
import com.staminal.venue.reviews.HallReview.Dto.UpdateReviewRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/customer")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/review-eligibility")
    public ReviewEligibilityResponse getEligibility(
            @RequestParam String enquiryId,
            Authentication authentication) {

        return reviewService.getEligibility(enquiryId, authentication);
    }

    @GetMapping("/reviews")
    public List<ReviewResponse> getReview(Authentication authentication) {
        return reviewService.getMyReviews(authentication);
    }

    @GetMapping("/reviews/{reviewId}")
    public ReviewResponse getReviewById(@PathVariable Long reviewId, Authentication authentication) {
        return reviewService.getReviewById(reviewId, authentication);
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
