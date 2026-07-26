package com.staminal.venue.notifications.queue;

public enum LeadNotificationEvaluationOutcome {
    QUEUED,
    SKIPPED_NOT_SUBSCRIBED,
    SKIPPED_PAUSED,
    SKIPPED_INVALID_CONSENT,
    LEGACY_NOT_RECORDED
}
