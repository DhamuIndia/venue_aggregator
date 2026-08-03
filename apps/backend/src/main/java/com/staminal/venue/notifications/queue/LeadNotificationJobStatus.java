package com.staminal.venue.notifications.queue;

public enum LeadNotificationJobStatus {
    QUEUED,
    PROCESSING,
    SENT,
    DELIVERED,
    READ,
    FAILED,
    CANCELLED
}
