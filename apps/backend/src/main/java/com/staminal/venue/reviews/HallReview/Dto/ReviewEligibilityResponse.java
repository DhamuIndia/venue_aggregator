package com.staminal.venue.reviews.HallReview.Dto;

public record ReviewEligibilityResponse(

        boolean eligible,

        String reason) {
}