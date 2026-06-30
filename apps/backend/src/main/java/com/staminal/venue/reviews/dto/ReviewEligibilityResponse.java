package com.staminal.venue.reviews.dto;

public record ReviewEligibilityResponse(

    boolean eligible,
    String reason

) {}