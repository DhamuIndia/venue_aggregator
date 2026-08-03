package com.staminal.venue.notifications.queue;

public enum LeadNotificationQueueOutcome {
    QUEUED,
    SKIPPED_LEAD_NOT_FOUND,
    SKIPPED_DIRECT_LEAD,
    SKIPPED_NOT_SUBSCRIBED,
    SKIPPED_PAUSED,
    SKIPPED_INVALID_CONSENT,
    SKIPPED_DUPLICATE
}
