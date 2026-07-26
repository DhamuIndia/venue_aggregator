package com.staminal.venue.notifications.queue;

public record LeadNotificationDispatchCandidate(
        Long jobId,
        Long vendorId,
        String destination,
        String templateKey,
        String templateLanguage,
        String vendorName,
        String serviceText,
        String eventDateText,
        String locationText,
        String budgetText) {
}
