package com.staminal.venue.notifications.whatsapp;

import java.util.Locale;
import java.util.Optional;

public enum MetaMessageDeliveryStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED;

    public static Optional<MetaMessageDeliveryStatus> fromProviderValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
