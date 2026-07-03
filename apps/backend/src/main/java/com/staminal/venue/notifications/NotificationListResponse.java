package com.staminal.venue.notifications;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        long unreadCount) {
}
