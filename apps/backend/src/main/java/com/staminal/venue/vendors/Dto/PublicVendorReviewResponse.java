package com.staminal.venue.vendors.Dto;

public record PublicVendorReviewResponse(
        String id,
        String customerName,
        int rating,
        String eventType,
        String comment,
        String eventDate,
        boolean verifiedService) {
}
