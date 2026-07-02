package com.staminal.venue.reviews.Dto;

public record PublicReviewResponse(
        Integer rating,
        String comment,
        Boolean verifiedService,
        String customerName) {
}