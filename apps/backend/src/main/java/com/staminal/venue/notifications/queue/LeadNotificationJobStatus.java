package com.staminal.venue.notifications.queue;

public enum LeadNotificationJobStatus {
    QUEUED,
    PROCESSING,
    SUBMITTED,
    SEND_FAILED,
    CANCELLED
}
