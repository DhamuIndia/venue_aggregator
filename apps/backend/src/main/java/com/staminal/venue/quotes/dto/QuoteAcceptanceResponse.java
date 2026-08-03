package com.staminal.venue.quotes.dto;

import java.util.List;

import com.staminal.venue.vendorbookings.dto.VendorServiceBookingResponse;

public record QuoteAcceptanceResponse(
        VendorQuoteResponse acceptedQuote,
        List<VendorQuoteResponse> requirementQuotes,
        VendorServiceBookingResponse booking) {
}
