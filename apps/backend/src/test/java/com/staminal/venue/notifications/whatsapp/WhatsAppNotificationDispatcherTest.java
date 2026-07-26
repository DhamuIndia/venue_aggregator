package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.staminal.venue.notifications.preferences.VendorNotificationPreference;
import com.staminal.venue.notifications.preferences.VendorNotificationPreferenceRepository;
import com.staminal.venue.notifications.preferences.WhatsAppConsentSource;
import com.staminal.venue.notifications.queue.LeadNotificationDispatchCandidate;
import com.staminal.venue.notifications.queue.LeadNotificationDispatchStateService;
import com.staminal.venue.vendors.Entity.Vendors;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificationDispatcherTest {

    @Mock
    private LeadNotificationDispatchStateService stateService;

    @Mock
    private VendorNotificationPreferenceRepository preferenceRepository;

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
                preferenceRepository,
                cloudApiClient);
    }

    @Test
    void disabledConfigurationDoesNotClaimOrSendAnyJob() {
        properties.setSendingEnabled(false);

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        assertThat(result).isEqualTo(WhatsAppDispatchBatchResult.disabled());
        verify(stateService, never()).claimQueued(any(Integer.class));
        verify(cloudApiClient, never()).sendTemplate(any());
    }

    @Test
    void submitsApprovedTemplateParametersAndStoresMetaMessageId() {
        LeadNotificationDispatchCandidate candidate = candidate();
        when(stateService.claimQueued(20)).thenReturn(List.of(candidate));
        when(preferenceRepository.findByVendor_Id(501L))
                .thenReturn(Optional.of(subscribedPreference()));
        when(cloudApiClient.sendTemplate(any(WhatsAppTemplateMessage.class)))
                .thenReturn(new WhatsAppCloudApiSendResult("wamid.test-message-123"));
        when(stateService.markSubmitted(701L, "wamid.test-message-123")).thenReturn(true);

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        ArgumentCaptor<WhatsAppTemplateMessage> messageCaptor =
                ArgumentCaptor.forClass(WhatsAppTemplateMessage.class);
        verify(cloudApiClient).sendTemplate(messageCaptor.capture());
        WhatsAppTemplateMessage message = messageCaptor.getValue();

        assertThat(message.destination()).isEqualTo("+919884012346");
        assertThat(message.templateName()).isEqualTo("new_matching_lead_v1");
        assertThat(message.languageCode()).isEqualTo("en");
        assertThat(message.bodyParameters()).containsExactly(
                "Saffron Leaf Catering",
                "Photography",
                "12 September 2026",
                "Adyar, Chennai",
                "₹75,000–₹1,50,000");
        verify(stateService).markSubmitted(701L, "wamid.test-message-123");
        assertThat(result).isEqualTo(new WhatsAppDispatchBatchResult(false, 1, 1, 0, 0));
    }

    @Test
    void currentPauseCancelsClaimedJobBeforeMetaCall() {
        LeadNotificationDispatchCandidate candidate = candidate();
        VendorNotificationPreference preference = subscribedPreference();
        preference.setWhatsAppLeadNotificationsPaused(true);
        preference.setWhatsAppPausedAt(Instant.parse("2026-07-26T08:00:00Z"));
        when(stateService.claimQueued(20)).thenReturn(List.of(candidate));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        verify(cloudApiClient, never()).sendTemplate(any());
        verify(stateService).markCancelled(701L);
        assertThat(result).isEqualTo(new WhatsAppDispatchBatchResult(false, 1, 0, 1, 0));
    }

    @Test
    void changedDestinationRequiresFreshQueueAndCancelsOldDestination() {
        LeadNotificationDispatchCandidate candidate = candidate();
        VendorNotificationPreference preference = subscribedPreference();
        preference.setWhatsAppNumber("+919999999999");
        when(stateService.claimQueued(20)).thenReturn(List.of(candidate));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));

        dispatcher.dispatchQueuedBatch();

        verify(cloudApiClient, never()).sendTemplate(any());
        verify(stateService).markCancelled(701L);
    }

    @Test
    void MetaSubmissionFailureIsRecordedWithoutAutomaticRetry() {
        LeadNotificationDispatchCandidate candidate = candidate();
        when(stateService.claimQueued(20)).thenReturn(List.of(candidate));
        when(preferenceRepository.findByVendor_Id(501L))
                .thenReturn(Optional.of(subscribedPreference()));
        when(cloudApiClient.sendTemplate(any()))
                .thenThrow(new WhatsAppCloudApiException("Meta unavailable"));

        WhatsAppDispatchBatchResult result = dispatcher.dispatchQueuedBatch();

        verify(stateService).markSendFailed(701L);
        verify(stateService, never()).markSubmitted(any(), any());
        assertThat(result).isEqualTo(new WhatsAppDispatchBatchResult(false, 1, 0, 0, 1));
    }

    private LeadNotificationDispatchCandidate candidate() {
        return new LeadNotificationDispatchCandidate(
                701L,
                501L,
                "+919884012346",
                "NEW_MATCHING_LEAD",
                "en",
                "Saffron Leaf Catering",
                "Photography",
                "12 September 2026",
                "Adyar, Chennai",
                "₹75,000–₹1,50,000");
    }

    private VendorNotificationPreference subscribedPreference() {
        Vendors vendor = new Vendors();
        vendor.setId(501L);

        VendorNotificationPreference preference = new VendorNotificationPreference();
        preference.setVendor(vendor);
        preference.setWhatsAppLeadNotificationsEnabled(true);
        preference.setWhatsAppLeadNotificationsPaused(false);
        preference.setWhatsAppNumber("+919884012346");
        preference.setWhatsAppConsentedAt(Instant.parse("2026-07-20T07:00:00Z"));
        preference.setWhatsAppConsentSource(WhatsAppConsentSource.VENDOR_SETTINGS);
        return preference;
    }
}
