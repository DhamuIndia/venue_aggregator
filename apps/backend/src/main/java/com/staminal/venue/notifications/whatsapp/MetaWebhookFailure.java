package com.staminal.venue.notifications.whatsapp;

public record MetaWebhookFailure(
        Integer code,
        String title,
        String reason,
        boolean temporary) {
}
