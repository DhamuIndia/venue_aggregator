package com.staminal.venue.reviews.HallReview.Dto;

public record PublicReviewResponse(
        Integer rating,
        String comment,
        Boolean verifiedService,
        String customerName) {
}