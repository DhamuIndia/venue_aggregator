package com.staminal.venue.customer.savedhalls.dto;

import java.util.List;

import com.staminal.venue.halls.Dto.HallResponse;

public record CustomerSavedHallsResponse(
        List<HallResponse> items,
        List<HallResponse> content,
        int total) {
}
