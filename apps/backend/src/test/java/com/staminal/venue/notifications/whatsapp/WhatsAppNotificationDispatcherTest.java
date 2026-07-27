package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.staminal.venue.notifications.queue.LeadNotificationDispatchCandidate;
import com.staminal.venue.notifications.queue.LeadNotificationDispatchStateService;
import com.staminal.venue.notifications.queue.LeadNotificationFailure;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificationDispatcherTest {

    @Mock
    private LeadNotificationDispatchStateService stateService;

    @Mock
    private WhatsAppVendorEligibilityService eligibilityService;

    @Mock
    private WhatsAppRetryPolicy retryPolicy;

    @Mock
    private WhatsAppCloudApiClient cloudApiClient;

    private WhatsAppCloudApiProperties properties;
    private WhatsAppNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = WhatsAppCloudApiPropertiesTest.readyProperties();
        dispatcher = new WhatsAppNotificationDispatcher(
                properties,
                stateService,
                eligibilityService,
                retryPolicy,
                cloudApiClient);
    }

    @Test
    void disabledConfigurationDoesNotClaimOrSendAnyJob() {
        properties.setSendingEnabled(false);

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        assertThat(result).isEqualTo(WhatsAppDispatchBatchResult.disabled());
        verify(stateService, never()).claimReady(
                any(Integer.class),
                any(Integer.class),
                any());
        verify(cloudApiClient, never()).sendTemplate(any());
    }

    @Test
    void emptyRolloutAllowlistDoesNotClaimOrSendWhenGlobalSendingIsEnabled() {
        properties.setRolloutAllowedVendorIds(Set.of());

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        assertThat(result).isEqualTo(new WhatsAppDispatchBatchResult(false, 0, 0, 0, 0));
        verify(stateService, never()).claimReady(any(Integer.class), any(Integer.class), any());
        verify(cloudApiClient, never()).sendTemplate(any());
    }

    @Test
    void submitsApprovedTemplateParametersAndStoresMetaMessageIdAsSent() {
        LeadNotificationDispatchCandidate candidate = candidate();
        when(stateService.claimReady(20, 3, Set.of(501L))).thenReturn(List.of(candidate));
        when(eligibilityService.isEligible(501L, "+919884012346")).thenReturn(true);
        when(cloudApiClient.sendTemplate(any(WhatsAppTemplateMessage.class)))
                .thenReturn(new WhatsAppCloudApiSendResult("wamid.test-message-123"));
        when(stateService.markSent(701L, 801L, "wamid.test-message-123")).thenReturn(true);

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        ArgumentCaptor<WhatsAppTemplateMessage> messageCaptor =
                ArgumentCaptor.forClass(WhatsAppTemplateMessage.class);
        verify(cloudApiClient).sendTemplate(messageCaptor.capture());
        assertThat(messageCaptor.getValue().bodyParameters()).containsExactly(
                "Saffron Leaf Catering",
                "Photography",
                "12 September 2026",
                "Adyar, Chennai",
                "₹75,000–₹1,50,000");
        assertThat(messageCaptor.getValue().dynamicUrlSuffix())
                .isEqualTo("LEAD-0123456789ABCDEF0123");
        verify(stateService).markSent(701L, 801L, "wamid.test-message-123");
        assertThat(result).isEqualTo(new WhatsAppDispatchBatchResult(false, 1, 1, 0, 0));
    }

    @Test
    void optedOutVendorCancelsAttemptBeforeMetaCall() {
        when(stateService.claimReady(20, 3, Set.of(501L))).thenReturn(List.of(candidate()));
        when(eligibilityService.isEligible(501L, "+919884012346")).thenReturn(false);

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        verify(cloudApiClient, never()).sendTemplate(any());
        verify(stateService).markCancelled(701L, 801L);
        assertThat(result).isEqualTo(new WhatsAppDispatchBatchResult(false, 1, 0, 1, 0));
    }

    @Test
    void candidateOutsideRolloutAllowlistIsCancelledBeforeMetaCall() {
        properties.setRolloutAllowedVendorIds(Set.of(999L));
        when(stateService.claimReady(20, 3, Set.of(999L))).thenReturn(List.of(candidate()));

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        verify(cloudApiClient, never()).sendTemplate(any());
        verify(stateService).markCancelled(701L, 801L);
        assertThat(result).isEqualTo(new WhatsAppDispatchBatchResult(false, 1, 0, 1, 0));
    }

    @Test
    void explicitTemporaryMetaFailureGetsBoundedRetry() {
        Instant retryAt = Instant.parse("2026-07-26T08:01:00Z");
        when(stateService.claimReady(20, 3, Set.of(501L))).thenReturn(List.of(candidate()));
        when(eligibilityService.isEligible(501L, "+919884012346")).thenReturn(true);
        when(cloudApiClient.sendTemplate(any()))
                .thenThrow(new WhatsAppCloudApiException(
                        130429,
                        "Rate limited",
                        "Cloud API throughput reached",
                        true,
                        true,
                        null));
        when(retryPolicy.nextRetryAt(1)).thenReturn(retryAt);

        dispatcher.dispatchQueuedBatch();

        verify(stateService).markFailed(
                701L,
                801L,
                new LeadNotificationFailure(
                        130429,
                        "Rate limited",
                        "Cloud API throughput reached",
                        true),
                retryAt);
    }

    @Test
    void ambiguousNetworkFailureIsNeverAutomaticallyRetried() {
        when(stateService.claimReady(20, 3, Set.of(501L))).thenReturn(List.of(candidate()));
        when(eligibilityService.isEligible(501L, "+919884012346")).thenReturn(true);
        when(cloudApiClient.sendTemplate(any()))
                .thenThrow(new WhatsAppCloudApiException(
                        null,
                        "Submission result unknown",
                        "Result unknown",
                        false,
                        false,
                        null));

        dispatcher.dispatchQueuedBatch();

        verify(retryPolicy, never()).nextRetryAt(any(Integer.class));
        verify(stateService).markFailed(
                701L,
                801L,
                new LeadNotificationFailure(
                        null,
                        "Submission result unknown",
                        "Result unknown",
                        false),
                null);
    }

    private LeadNotificationDispatchCandidate candidate() {
        return new LeadNotificationDispatchCandidate(
                701L,
                801L,
                1,
                501L,
                "+919884012346",
                "NEW_MATCHING_LEAD",
                "en",
                "LEAD-0123456789ABCDEF0123",
                "Saffron Leaf Catering",
                "Photography",
                "12 September 2026",
                "Adyar, Chennai",
                "₹75,000–₹1,50,000");
    }
}
