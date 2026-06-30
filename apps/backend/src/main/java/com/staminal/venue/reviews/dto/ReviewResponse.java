package com.staminal.venue.reviews.dto;

import java.time.Instant;

public record ReviewResponse(

    Long id,
    Integer rating,
    String title,
    String comment,
    Boolean verifiedService,
    Instant createdAt

) {}