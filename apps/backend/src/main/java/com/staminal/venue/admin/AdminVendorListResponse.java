package com.staminal.venue.admin;

import java.util.List;

public record AdminVendorListResponse(
        List<AdminVendorResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
