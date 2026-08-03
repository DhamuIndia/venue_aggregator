package com.staminal.venue.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        List<String> imageUrls,
        String status,
        String rejectionReason,
        Long reviewedBy,
        LocalDateTime reviewedAt) {
}
