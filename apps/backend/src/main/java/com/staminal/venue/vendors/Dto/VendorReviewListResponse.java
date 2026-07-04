package com.staminal.venue.vendors.Dto;

import java.util.List;

public record VendorReviewListResponse(
        List<PublicVendorReviewResponse> reviews,
        int reviewCount,
        double averageRating) {
}
