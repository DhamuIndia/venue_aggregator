package com.staminal.venue.notifications.whatsapp;

public record WhatsAppWebhookProcessingResult(
        int receivedStatuses,
        int matchedStatuses,
        int ignoredStatuses) {
}
