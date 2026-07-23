package com.staminal.venue.requirements.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.PreferredContactChannel;

public record CustomerRequirementResponse(
        Long id,
        String eventType,
        LocalDate eventDate,
        String location,
        String city,
        String pincode,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        Integer guestCount,
        String details,
        PreferredContactChannel preferredContactChannel,
        boolean shareContactDetails,
        CustomerRequirementStatus status,
        List<CustomerRequirementCategoryResponse> services,
        long matchedVendorCount,
        Instant createdAt,
        Instant updatedAt) {
}
