package com.staminal.venue.reviews.Dto;

import java.time.Instant;

public record ReviewResponse(

        Long id,

        Long bookingId,

        Long enquiryId,

        Long hallId,

        String hallName,

        Integer rating,

        String comment,

        Boolean verifiedService,

        Instant createdAt,

        Instant updatedAt) {
}