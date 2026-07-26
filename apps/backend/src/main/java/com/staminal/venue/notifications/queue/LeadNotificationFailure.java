package com.staminal.venue.notifications.queue;

public record LeadNotificationFailure(
        Integer code,
        String title,
        String reason,
        boolean temporary) {
}
