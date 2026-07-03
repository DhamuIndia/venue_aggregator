package com.staminal.venue.reports;

import java.math.BigDecimal;
import java.util.List;

public record OwnerReportResponse(
        long enquiries,
        long confirmedBookings,
        long completedBookings,
        BigDecimal estimatedRevenue,
        int conversionRate,
        double averageRating,
        int occupancyRate,
        List<TrendPointResponse> trends,
        List<EventMixResponse> eventMix) {
}
