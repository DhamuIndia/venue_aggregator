package com.staminal.venue.reviews.Dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(

        @NotNull
        Long enquiryId,

        @NotNull
        @Min(1)
        @Max(5)
        Integer rating,

        @NotBlank
        @Size(min = 10, max = 500)
        String comment) {
}