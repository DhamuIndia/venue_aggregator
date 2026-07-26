package com.staminal.venue.notifications.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MarketplaceVendorLeadNotificationListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MarketplaceVendorLeadNotificationListener.class);

    private final LeadNotificationQueueService queueService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void queueAfterLeadCommit(MarketplaceVendorLeadCreatedEvent event) {
        try {
            queueService.enqueueMarketplaceLead(event.vendorLeadId());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not queue WhatsApp notification for vendor lead {}",
                    event.vendorLeadId(),
                    exception);
        }
    }
}
