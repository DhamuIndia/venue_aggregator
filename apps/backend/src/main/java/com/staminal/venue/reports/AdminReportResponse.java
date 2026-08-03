package com.staminal.venue.reports;

import java.math.BigDecimal;
import java.util.List;

public record AdminReportResponse(
        long totalUsers,
        long activeListings,
        long monthlyEnquiries,
        long confirmedBookings,
        BigDecimal bookingRevenue,
        BigDecimal vendorRevenue,
        int conversionRate,
        List<TrendPointResponse> trends,
        List<TopCityResponse> topCities) {
}
