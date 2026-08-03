package com.staminal.venue.admin;

import java.util.List;

public record AdminReviewListResponse(
        List<AdminReviewResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
