package com.staminal.venue.reviews.VendorReview;

public record VendorReviewEligibilityResponse(

        boolean eligible,

        String leadId,

        String vendorName,

        String eventDate,

        String eventType,

        String reason

) {
}