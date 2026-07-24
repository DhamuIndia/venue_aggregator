package com.staminal.venue.requirements.dto;

import java.util.List;

public record CustomerRequirementOptionsResponse(
        boolean enabled,
        List<CustomerRequirementCategoryResponse> categories) {
}
