package com.staminal.venue.bookings.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.SlotType;

public record BookingResponse(
        String id,
        String bookingId,
        String enquiryId,
        String hallId,
        String hallName,
        String customerId,
        String customerName,
        LocalDate eventDate,
        String eventType,
        Integer guestCount,
        SlotType slot,
        BookingStatus status,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        String notes,
        Instant confirmedAt,
        Instant updatedAt) {
}
