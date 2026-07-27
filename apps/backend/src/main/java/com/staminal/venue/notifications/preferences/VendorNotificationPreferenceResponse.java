package com.staminal.venue.notifications.preferences;

import java.time.Instant;

public record VendorNotificationPreferenceResponse(
        Long vendorId,
        boolean whatsAppLeadNotificationsEnabled,
        boolean whatsAppLeadNotificationsPaused,
        boolean canReceiveWhatsAppLeadNotifications,
        String whatsAppNumber,
        Instant whatsAppConsentedAt,
        WhatsAppConsentSource whatsAppConsentSource,
        Instant whatsAppOptedOutAt,
        Instant whatsAppPausedAt,
        Instant updatedAt) {
}
