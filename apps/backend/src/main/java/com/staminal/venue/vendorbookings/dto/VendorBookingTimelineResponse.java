package com.staminal.venue.vendorbookings.dto;

import java.time.Instant;

public record VendorBookingTimelineResponse(
        String id,
        String eventType,
        String fromStatus,
        String toStatus,
        String actorRole,
        String message,
        Instant createdAt) {
}
