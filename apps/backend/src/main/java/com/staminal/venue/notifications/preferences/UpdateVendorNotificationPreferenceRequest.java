package com.staminal.venue.notifications.preferences;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateVendorNotificationPreferenceRequest(
        @NotNull(message = "WhatsApp lead notification selection is required")
        Boolean whatsAppLeadNotificationsEnabled,

        @NotNull(message = "WhatsApp pause selection is required")
        Boolean whatsAppLeadNotificationsPaused,

        @Size(max = 20, message = "WhatsApp number must be 20 characters or fewer")
        String whatsAppNumber,

        Boolean consentConfirmed) {
}
