package com.staminal.venue.notifications;

import java.time.Instant;

public record NotificationResponse(
        String id,
        NotificationType type,
        String title,
        String message,
        Instant createdAt,
        Instant readAt,
        String actionHref) {
}
