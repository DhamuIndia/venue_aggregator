package com.staminal.venue.reviews.dto;

public record UpdateReviewRequest(

    Integer rating,
    String title,
    String comment

) {}