package com.staminal.venue.vendors.Dto;

import java.util.List;

public record PublicVendorListResponse(
        List<PublicVendorResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
