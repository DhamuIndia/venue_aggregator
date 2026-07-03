package com.staminal.venue.reports;

public record TopCityResponse(
        String city,
        long enquiries,
        long bookings) {
}
