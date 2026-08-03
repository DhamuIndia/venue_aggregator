package com.staminal.venue.enquiries.dto;

import java.time.LocalDate;
import java.util.List;

import com.staminal.venue.enums.SlotType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEnquiryRequest(
        @NotBlank String hallId,
        @NotNull @Future LocalDate eventDate,
        @NotBlank @Size(max = 120) String eventType,
        @NotNull @Min(1) Integer guestCount,
        @NotNull SlotType slot,
        @Valid List<EnquirySlotRequestDto> slotRequests,
        @Size(max = 1000) String notes) {
}
