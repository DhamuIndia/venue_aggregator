package com.staminal.venue.admin;

import com.staminal.venue.reviews.HallReview.ReviewModerationStatus;

public record AdminReviewModerationRequest(
        ReviewModerationStatus status,
        String reason) {
}
