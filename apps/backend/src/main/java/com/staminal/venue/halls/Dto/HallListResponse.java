package com.staminal.venue.halls.Dto;

import java.util.List;

public record HallListResponse(
        List<HallResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
