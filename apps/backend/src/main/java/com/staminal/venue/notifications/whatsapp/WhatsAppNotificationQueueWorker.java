package com.staminal.venue.notifications.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.notifications.whatsapp",
        name = "sending-enabled",
        havingValue = "true")
public class WhatsAppNotificationQueueWorker {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WhatsAppNotificationQueueWorker.class);

    private final WhatsAppNotificationDispatcher dispatcher;

    @Scheduled(
            fixedDelayString = "${app.notifications.whatsapp.poll-interval-ms:15000}",
            initialDelayString = "${app.notifications.whatsapp.initial-delay-ms:15000}")
    public void dispatchQueuedNotifications() {
        try {
            WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();
            if (result.claimed() > 0) {
                LOGGER.info(
                        "WhatsApp queue batch completed: claimed={}, submitted={}, cancelled={}, failed={}",
                        result.claimed(),
                        result.submitted(),
                        result.cancelled(),
                        result.failed());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("WhatsApp queue batch could not start", exception);
        }
    }
}
