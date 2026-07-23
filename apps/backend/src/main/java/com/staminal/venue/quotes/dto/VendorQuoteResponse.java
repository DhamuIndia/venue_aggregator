package com.staminal.venue.quotes.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.staminal.venue.enums.VendorQuoteStatus;

public record VendorQuoteResponse(
        Long id,
        Long leadId,
        Long requirementId,
        String vendorId,
        String vendorName,
        String service,
        BigDecimal amount,
        String packageName,
        String serviceDescription,
        List<String> inclusions,
        BigDecimal additionalCharges,
        String additionalChargesDescription,
        BigDecimal totalAmount,
        String notes,
        LocalDate validUntil,
        VendorQuoteStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
