package com.staminal.venue.admin;

import java.time.Instant;

public record AdminVendorResponse(
        String id,
        String businessName,
        String contactName,
        String category,
        String city,
        Instant submittedAt,
        String status,
        String rejectionReason,
        String reviewedBy,
        Instant reviewedAt) {
}
