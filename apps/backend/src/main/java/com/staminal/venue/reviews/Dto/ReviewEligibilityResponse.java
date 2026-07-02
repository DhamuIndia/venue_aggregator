package com.staminal.venue.reviews.Dto;

public record ReviewEligibilityResponse(

        boolean eligible,

        String reason) {
}