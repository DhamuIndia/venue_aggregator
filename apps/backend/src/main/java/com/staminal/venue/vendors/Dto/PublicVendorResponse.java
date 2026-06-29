package com.staminal.venue.vendors.Dto;

import java.math.BigDecimal;
import java.util.List;

public record PublicVendorResponse(
        String id,
        String businessName,
        String ownerName,
        String category,
        String city,
        String area,
        double rating,
        int reviewCount,
        BigDecimal startingPrice,
        String imageUrl,
        List<String> galleryUrls,
        boolean verified,
        String responseTime,
        int completedEvents,
        List<String> services,
        String description,
        List<PublicVendorPackageResponse> packages,
        List<PublicVendorReviewResponse> reviews,
        String status) {
}
