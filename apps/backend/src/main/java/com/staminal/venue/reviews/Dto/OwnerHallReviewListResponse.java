package com.staminal.venue.reviews.Dto;

import java.util.List;

public record OwnerHallReviewListResponse(
        List<OwnerHallReviewResponse> reviews,
        int totalReviews,
        double averageRating) {
}
