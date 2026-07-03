package com.staminal.venue.admin;

import java.time.Instant;

public record AdminReviewResponse(
        String id,
        String hallName,
        String customerName,
        Integer rating,
        String comment,
        String reportReason,
        Boolean verifiedService,
        String status,
        Instant submittedAt,
        Long moderatedBy,
        Instant moderatedAt) {
}
