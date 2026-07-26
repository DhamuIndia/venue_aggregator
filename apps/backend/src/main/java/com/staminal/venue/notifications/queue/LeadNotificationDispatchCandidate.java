package com.staminal.venue.notifications.queue;

public record LeadNotificationDispatchCandidate(
        Long jobId,
        Long attemptId,
        int attemptNumber,
        Long vendorId,
        String destination,
        String templateKey,
        String templateLanguage,
        String leadReference,
        String vendorName,
        String serviceText,
        String eventDateText,
        String locationText,
        String budgetText) {
}
