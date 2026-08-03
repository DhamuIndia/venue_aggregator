package com.staminal.venue.enquiries.dto;

import com.staminal.venue.enums.EnquiryStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateEnquiryStatusRequest(
        @NotNull EnquiryStatus status,
        @Size(max = 1000) String message) {
}
