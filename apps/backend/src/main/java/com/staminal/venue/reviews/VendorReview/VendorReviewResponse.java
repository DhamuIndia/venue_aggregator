package com.staminal.venue.reviews.VendorReview;

import java.time.Instant;

public record VendorReviewResponse(

        Long id,

        Long leadId,

        Long vendorId,

        String vendorName,

        Integer rating,

        String comment,

        Instant createdAt

) {
}