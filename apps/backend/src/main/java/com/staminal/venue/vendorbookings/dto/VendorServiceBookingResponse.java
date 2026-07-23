package com.staminal.venue.vendorbookings.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.VendorServiceBookingStatus;

public record VendorServiceBookingResponse(
        String id,
        Long quoteId,
        Long leadId,
        Long requirementId,
        String vendorId,
        String vendorName,
        String customerId,
        String customerName,
        String customerPhone,
        String customerEmail,
        String service,
        String packageName,
        String eventType,
        LocalDate eventDate,
        String location,
        BigDecimal amount,
        VendorServiceBookingStatus status,
        PaymentStatus paymentStatus,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt) {
}
