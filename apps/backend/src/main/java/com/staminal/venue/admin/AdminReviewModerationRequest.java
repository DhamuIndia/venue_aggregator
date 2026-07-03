package com.staminal.venue.admin;

import com.staminal.venue.reviews.ReviewModerationStatus;

public record AdminReviewModerationRequest(
        ReviewModerationStatus status,
        String reason) {
}
