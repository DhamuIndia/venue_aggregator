package com.staminal.venue.reports;

import java.math.BigDecimal;

public record TrendPointResponse(
        String label,
        long enquiries,
        long bookings,
        BigDecimal revenue) {
}
