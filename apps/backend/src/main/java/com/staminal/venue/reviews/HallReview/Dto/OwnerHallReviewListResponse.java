package com.staminal.venue.reviews.HallReview.Dto;

import java.util.List;

public record OwnerHallReviewListResponse(
        List<OwnerHallReviewResponse> reviews,
        int totalReviews,
        double averageRating) {
}
