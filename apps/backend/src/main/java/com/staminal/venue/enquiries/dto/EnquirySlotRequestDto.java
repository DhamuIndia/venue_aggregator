package com.staminal.venue.enquiries.dto;

import java.time.LocalDate;

import com.staminal.venue.enums.SlotType;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record EnquirySlotRequestDto(
        @NotNull @Future LocalDate date,
        @NotNull SlotType slot) {
}
