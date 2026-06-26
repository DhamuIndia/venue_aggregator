package com.staminal.venue.bookings.dto;

import java.util.List;

public record BookingListResponse(
        List<BookingResponse> items,
        int total) {
}
