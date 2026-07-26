package com.staminal.venue.notifications.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.preferences.VendorNotificationPreference;
import com.staminal.venue.notifications.preferences.VendorNotificationPreferenceRepository;
import com.staminal.venue.notifications.preferences.WhatsAppConsentSource;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.vendors.Entity.Vendors;

@ExtendWith(MockitoExtension.class)
class LeadNotificationQueueServiceTest {

    @Mock
    private VendorLeadRepository vendorLeadRepository;

    @Mock
    private VendorNotificationPreferenceRepository preferenceRepository;

    @Mock
    private LeadNotificationJobRepository notificationJobRepository;

    @Mock
    private LeadNotificationEvaluationRepository evaluationRepository;

    private LeadNotificationQueueService service;

    @BeforeEach
    void setUp() {
        service = new LeadNotificationQueueService(
                vendorLeadRepository,
                preferenceRepository,
                notificationJobRepository,
                evaluationRepository);
    }

    @Test
    void subscribedVendorGetsOnePrivacySafeFrozenQueueJob() {
        VendorLead lead = marketplaceLead();
        VendorNotificationPreference preference = subscribedPreference(lead.getVendor());

        when(vendorLeadRepository.findById(901L)).thenReturn(Optional.of(lead));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));
        when(notificationJobRepository.save(any(LeadNotificationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeadNotificationQueueOutcome result = service.enqueueMarketplaceLead(901L);

        ArgumentCaptor<LeadNotificationJob> jobCaptor = ArgumentCaptor.forClass(LeadNotificationJob.class);
        verify(notificationJobRepository).save(jobCaptor.capture());
        LeadNotificationJob job = jobCaptor.getValue();

        assertThat(result).isEqualTo(LeadNotificationQueueOutcome.QUEUED);
        assertThat(job.getVendorLead()).isSameAs(lead);
        assertThat(job.getVendor()).isSameAs(lead.getVendor());
        assertThat(job.getRequirement()).isSameAs(lead.getRequirement());
        assertThat(job.getChannel()).isEqualTo(NotificationChannel.WHATSAPP);
        assertThat(job.getNotificationType()).isEqualTo(LeadNotificationType.LEAD_MATCHED);
        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.QUEUED);
        assertThat(job.getDestination()).isEqualTo("+919884012346");
        assertThat(job.getTemplateKey()).isEqualTo("NEW_MATCHING_LEAD");
        assertThat(job.getTemplateLanguage()).isEqualTo("en");
        assertThat(job.getLeadReference()).isEqualTo("LEAD-0123456789ABCDEF0123");
        assertThat(job.getVendorName()).isEqualTo("Saffron Leaf Catering");
        assertThat(job.getServiceText()).isEqualTo("Photography");
        assertThat(job.getEventTypeText()).isEqualTo("Wedding");
        assertThat(job.getEventDateText()).isEqualTo("12 September 2026");
        assertThat(job.getLocationText()).isEqualTo("Adyar, Chennai");
        assertThat(job.getBudgetText()).isEqualTo("₹75,000–₹1,50,000");

        ArgumentCaptor<LeadNotificationEvaluation> evaluationCaptor =
                ArgumentCaptor.forClass(LeadNotificationEvaluation.class);
        verify(evaluationRepository).save(evaluationCaptor.capture());
        LeadNotificationEvaluation evaluation = evaluationCaptor.getValue();
        assertThat(evaluation.getOutcome()).isEqualTo(LeadNotificationEvaluationOutcome.QUEUED);
        assertThat(evaluation.isSubscribedAtEvaluation()).isTrue();
        assertThat(evaluation.getNotificationJob()).isSameAs(job);
        assertThat(evaluation.getEvaluatedAt()).isNotNull();
    }

    @Test
    void vendorWithoutPreferenceIsNotQueued() {
        VendorLead lead = marketplaceLead();
        when(vendorLeadRepository.findById(901L)).thenReturn(Optional.of(lead));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.empty());

        assertThat(service.enqueueMarketplaceLead(901L))
                .isEqualTo(LeadNotificationQueueOutcome.SKIPPED_NOT_SUBSCRIBED);
        verify(notificationJobRepository, never()).save(any());
        ArgumentCaptor<LeadNotificationEvaluation> evaluationCaptor =
                ArgumentCaptor.forClass(LeadNotificationEvaluation.class);
        verify(evaluationRepository).save(evaluationCaptor.capture());
        assertThat(evaluationCaptor.getValue().getOutcome())
                .isEqualTo(LeadNotificationEvaluationOutcome.SKIPPED_NOT_SUBSCRIBED);
        assertThat(evaluationCaptor.getValue().getSkipReason())
                .contains("not subscribed");
    }

    @Test
    void vendorWhoHasNotEnabledAlertsIsNotQueued() {
        VendorLead lead = marketplaceLead();
        VendorNotificationPreference preference = subscribedPreference(lead.getVendor());
        preference.setWhatsAppLeadNotificationsEnabled(false);

        when(vendorLeadRepository.findById(901L)).thenReturn(Optional.of(lead));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));

        assertThat(service.enqueueMarketplaceLead(901L))
                .isEqualTo(LeadNotificationQueueOutcome.SKIPPED_NOT_SUBSCRIBED);
        verify(notificationJobRepository, never()).save(any());
    }

    @Test
    void pausedVendorIsNotQueued() {
        VendorLead lead = marketplaceLead();
        VendorNotificationPreference preference = subscribedPreference(lead.getVendor());
        preference.setWhatsAppLeadNotificationsPaused(true);
        preference.setWhatsAppPausedAt(Instant.parse("2026-07-25T08:00:00Z"));

        when(vendorLeadRepository.findById(901L)).thenReturn(Optional.of(lead));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));

        assertThat(service.enqueueMarketplaceLead(901L))
                .isEqualTo(LeadNotificationQueueOutcome.SKIPPED_PAUSED);
        verify(notificationJobRepository, never()).save(any());
    }

    @Test
    void incompleteOrWithdrawnConsentIsNotQueued() {
        VendorLead lead = marketplaceLead();
        VendorNotificationPreference preference = subscribedPreference(lead.getVendor());
        preference.setWhatsAppOptedOutAt(Instant.parse("2026-07-25T09:00:00Z"));

        when(vendorLeadRepository.findById(901L)).thenReturn(Optional.of(lead));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));

        assertThat(service.enqueueMarketplaceLead(901L))
                .isEqualTo(LeadNotificationQueueOutcome.SKIPPED_INVALID_CONSENT);
        verify(notificationJobRepository, never()).save(any());
    }

    @Test
    void duplicateLeadNotificationIsNotQueuedAgain() {
        VendorLead lead = marketplaceLead();
        VendorNotificationPreference preference = subscribedPreference(lead.getVendor());

        when(vendorLeadRepository.findById(901L)).thenReturn(Optional.of(lead));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));
        LeadNotificationJob existingJob = new LeadNotificationJob();
        existingJob.setId(601L);
        when(notificationJobRepository.findByVendorLead_IdAndChannelAndNotificationType(
                901L,
                NotificationChannel.WHATSAPP,
                LeadNotificationType.LEAD_MATCHED))
                .thenReturn(Optional.of(existingJob));

        assertThat(service.enqueueMarketplaceLead(901L))
                .isEqualTo(LeadNotificationQueueOutcome.SKIPPED_DUPLICATE);
        verify(notificationJobRepository, never()).save(any());
        ArgumentCaptor<LeadNotificationEvaluation> evaluationCaptor =
                ArgumentCaptor.forClass(LeadNotificationEvaluation.class);
        verify(evaluationRepository).save(evaluationCaptor.capture());
        assertThat(evaluationCaptor.getValue().getOutcome())
                .isEqualTo(LeadNotificationEvaluationOutcome.QUEUED);
        assertThat(evaluationCaptor.getValue().getNotificationJob()).isSameAs(existingJob);
    }

    @Test
    void directVendorEnquiryIsNotAddedToMarketplaceQueue() {
        VendorLead lead = marketplaceLead();
        lead.setRequirement(null);
        when(vendorLeadRepository.findById(901L)).thenReturn(Optional.of(lead));

        assertThat(service.enqueueMarketplaceLead(901L))
                .isEqualTo(LeadNotificationQueueOutcome.SKIPPED_DIRECT_LEAD);
        verify(preferenceRepository, never()).findByVendor_Id(any());
        verify(notificationJobRepository, never()).save(any());
    }

    private VendorLead marketplaceLead() {
        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setBusinessName("Saffron Leaf Catering");

        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(801L);
        requirement.setEventType("Wedding");
        requirement.setEventDate(LocalDate.parse("2026-09-12"));
        requirement.setLocation("Adyar");
        requirement.setCity("Chennai");
        requirement.setBudgetMin(new BigDecimal("75000"));
        requirement.setBudgetMax(new BigDecimal("150000"));

        VendorLead lead = new VendorLead();
        lead.setId(901L);
        lead.setPublicReference("LEAD-0123456789ABCDEF0123");
        lead.setVendor(vendor);
        lead.setRequirement(requirement);
        lead.setService("Photography");
        lead.setEventType("Wedding");
        lead.setEventDate(LocalDate.parse("2026-09-12"));
        lead.setLocation("Adyar, Chennai");
        return lead;
    }

    private VendorNotificationPreference subscribedPreference(Vendors vendor) {
        VendorNotificationPreference preference = new VendorNotificationPreference();
        preference.setId(701L);
        preference.setVendor(vendor);
        preference.setWhatsAppLeadNotificationsEnabled(true);
        preference.setWhatsAppLeadNotificationsPaused(false);
        preference.setWhatsAppNumber("+919884012346");
        preference.setWhatsAppConsentedAt(Instant.parse("2026-07-20T07:00:00Z"));
        preference.setWhatsAppConsentSource(WhatsAppConsentSource.VENDOR_SETTINGS);
        return preference;
    }
}
