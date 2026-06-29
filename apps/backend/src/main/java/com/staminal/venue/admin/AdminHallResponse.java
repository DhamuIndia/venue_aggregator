package com.staminal.venue.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminHallResponse(
        String id,
        String name,
        String ownerName,
        String ownerPhone,
        String location,
        String venueType,
        Integer capacity,
        BigDecimal startingPrice,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        String imageUrl,
        String status,
        String rejectionReason,
        String reviewedBy,
        LocalDateTime reviewedAt) {
}
