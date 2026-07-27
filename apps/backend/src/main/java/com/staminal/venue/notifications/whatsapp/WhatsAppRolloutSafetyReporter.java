package com.staminal.venue.notifications.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WhatsAppRolloutSafetyReporter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WhatsAppRolloutSafetyReporter.class);

    private final WhatsAppCloudApiProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void reportEffectiveRolloutState() {
        int allowedVendorCount = properties.getRolloutAllowedVendorIds().size();
        if (!properties.isSendingEnabled()) {
            LOGGER.info(
                    "WhatsApp sending is disabled; controlled rollout allowlist contains {} vendor(s)",
                    allowedVendorCount);
            return;
        }
        if (allowedVendorCount == 0) {
            LOGGER.warn(
                    "WhatsApp sending is enabled but the rollout allowlist is empty; all dispatch is blocked");
            return;
        }
        LOGGER.warn(
                "WhatsApp controlled rollout is active for {} allowlisted vendor(s)",
                allowedVendorCount);
    }
}
