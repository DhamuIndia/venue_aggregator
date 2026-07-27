package com.staminal.venue.notifications.queue;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class MarketplaceVendorLeadNotificationListenerTest {

    @Mock
    private LeadNotificationQueueService queueService;

    @Test
    void queueFailureIsContainedAfterLeadCommit() {
        MarketplaceVendorLeadNotificationListener listener =
                new MarketplaceVendorLeadNotificationListener(queueService);
        doThrow(new IllegalStateException("queue table unavailable"))
                .when(queueService)
                .enqueueMarketplaceLead(901L);

        assertThatCode(() -> listener.queueAfterLeadCommit(new MarketplaceVendorLeadCreatedEvent(901L)))
                .doesNotThrowAnyException();
        verify(queueService).enqueueMarketplaceLead(901L);
    }

    @Test
    void listenerAndQueueUseIsolatedAfterCommitTransaction() throws NoSuchMethodException {
        Method listenerMethod = MarketplaceVendorLeadNotificationListener.class.getMethod(
                "queueAfterLeadCommit",
                MarketplaceVendorLeadCreatedEvent.class);
        TransactionalEventListener listenerAnnotation =
                listenerMethod.getAnnotation(TransactionalEventListener.class);

        Method queueMethod = LeadNotificationQueueService.class.getMethod(
                "enqueueMarketplaceLead",
                Long.class);
        Transactional transactionAnnotation = queueMethod.getAnnotation(Transactional.class);

        org.assertj.core.api.Assertions.assertThat(listenerAnnotation).isNotNull();
        org.assertj.core.api.Assertions.assertThat(listenerAnnotation.phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
        org.assertj.core.api.Assertions.assertThat(transactionAnnotation).isNotNull();
        org.assertj.core.api.Assertions.assertThat(transactionAnnotation.propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
