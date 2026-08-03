package com.staminal.venue.reports;

import java.math.BigDecimal;
import java.util.List;

public record VendorReportResponse(
        long leads,
        long contacted,
        long quotesSent,
        long booked,
        BigDecimal bookedValue,
        int conversionRate,
        BigDecimal averageBudget,
        int responseRate,
        List<TrendPointResponse> trends,
        List<ServiceMixResponse> serviceMix) {
}
