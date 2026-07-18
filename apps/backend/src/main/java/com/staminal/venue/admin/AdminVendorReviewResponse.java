package com.staminal.venue.admin;

import java.time.Instant;

public record AdminVendorReviewResponse(

        String id,

        String vendorName,

        String customerName,

        Integer rating,

        String comment,

        String moderationReason,

        Boolean verifiedService,

        String status,

        Instant createdAt,

        Long moderatedByAdminId,

        Instant moderatedAt) {
}