package com.staminal.venue.admin;

import java.util.List;

public record AdminHallListResponse(
        List<AdminHallResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
