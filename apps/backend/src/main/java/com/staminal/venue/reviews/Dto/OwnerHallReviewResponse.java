package com.staminal.venue.reviews.Dto;

public record OwnerHallReviewResponse(
        String id,
        String customerName,
        Integer rating,
        String eventType,
        String eventDate,
        String comment,
        Boolean verifiedService) {
}
