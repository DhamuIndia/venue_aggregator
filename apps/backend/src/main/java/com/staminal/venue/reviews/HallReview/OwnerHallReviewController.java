package com.staminal.venue.reviews.HallReview;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.reviews.HallReview.Dto.OwnerHallReviewListResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/owner/halls")
public class OwnerHallReviewController {

    private final OwnerHallReviewService ownerHallReviewService;

    @GetMapping("/{hallId}/reviews")
    public OwnerHallReviewListResponse getHallReviews(
            @PathVariable Long hallId,
            Authentication authentication) {
        return ownerHallReviewService.getHallReviews(hallId, authentication);
    }
}
