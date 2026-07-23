package com.staminal.venue.vendorbookings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelVendorBookingRequest(
        @NotBlank @Size(max = 1000) String reason) {
}
