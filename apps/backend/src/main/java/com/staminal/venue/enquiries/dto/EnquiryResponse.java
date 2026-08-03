package com.staminal.venue.enquiries.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.enums.SlotType;

public record EnquiryResponse(
        String id,
        String hallId,
        String hallName,
        String customerId,
        String customerName,
        String customerPhone,
        String customerEmail,
        LocalDate eventDate,
        String eventType,
        Integer guestCount,
        SlotType slot,
        List<EnquirySlotRequestDto> slotRequests,
        String notes,
        EnquiryStatus status,
        Instant submittedAt,
        Instant updatedAt,
        String ownerResponseMessage,
        Long version) {
}
