package com.staminal.venue.vendorbookings.dto;

import com.staminal.venue.enums.VendorServiceBookingStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateVendorBookingStatusRequest(
        @NotNull VendorServiceBookingStatus status,
        @Size(max = 1000) String reason) {
}
