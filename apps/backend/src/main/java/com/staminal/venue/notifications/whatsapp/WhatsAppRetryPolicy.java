package com.staminal.venue.notifications.whatsapp;

import java.time.Instant;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WhatsAppRetryPolicy {

    private final WhatsAppCloudApiProperties properties;

    public Instant nextRetryAt(int completedAttemptCount) {
        if (completedAttemptCount >= properties.getMaxAttempts()) {
            return null;
        }

        int exponent = Math.max(0, completedAttemptCount - 1);
        long delay = properties.getRetryInitialDelayMs();
        for (int index = 0; index < exponent; index++) {
            if (delay >= properties.getRetryMaxDelayMs() / 2) {
                delay = properties.getRetryMaxDelayMs();
                break;
            }
            delay *= 2;
        }
        return Instant.now().plusMillis(Math.min(delay, properties.getRetryMaxDelayMs()));
    }
}
