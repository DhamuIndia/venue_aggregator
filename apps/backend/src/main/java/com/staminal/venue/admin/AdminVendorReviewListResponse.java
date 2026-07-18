package com.staminal.venue.admin;

import java.util.List;

public record AdminVendorReviewListResponse(

        List<AdminVendorReviewResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages) {
}