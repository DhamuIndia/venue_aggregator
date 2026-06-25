package com.staminal.venue.bookings.dto;

import com.staminal.venue.enums.BookingStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBookingStatusRequest(
        @NotNull BookingStatus status,
        @Size(max = 1000) String reason) {
}
