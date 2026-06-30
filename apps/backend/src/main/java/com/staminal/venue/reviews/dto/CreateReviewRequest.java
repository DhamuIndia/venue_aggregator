package com.staminal.venue.reviews.dto;

public record CreateReviewRequest(

    Long enquiryId,
    Integer rating,
    String title,
    String comment

) {}