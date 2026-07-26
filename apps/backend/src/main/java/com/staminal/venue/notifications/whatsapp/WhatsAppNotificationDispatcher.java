package com.staminal.venue.notifications.whatsapp;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.staminal.venue.notifications.preferences.VendorNotificationPreference;
import com.staminal.venue.notifications.preferences.VendorNotificationPreferenceRepository;
import com.staminal.venue.notifications.queue.LeadNotificationDispatchCandidate;
import com.staminal.venue.notifications.queue.LeadNotificationDispatchStateService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppNotificationDispatcher {

    private static final String LEAD_TEMPLATE_KEY = "NEW_MATCHING_LEAD";

    private final WhatsAppCloudApiProperties properties;
    private final LeadNotificationDispatchStateService stateService;
    private final VendorNotificationPreferenceRepository preferenceRepository;
    private final WhatsAppCloudApiClient cloudApiClient;

    public WhatsAppDispatchBatchResult dispatchQueuedBatch() {
        if (!properties.isSendingEnabled()) {
            return WhatsAppDispatchBatchResult.disabled();
        }

        properties.validateForSending();
        List<LeadNotificationDispatchCandidate> candidates =
                stateService.claimQueued(properties.getBatchSize());
        int submitted = 0;
        int cancelled = 0;
        int failed = 0;

        for (LeadNotificationDispatchCandidate candidate : candidates) {
            if (!isCurrentlyEligible(candidate)) {
                stateService.markCancelled(candidate.jobId());
                cancelled++;
                continue;
            }

            WhatsAppCloudApiSendResult result;
            try {
                result = cloudApiClient.sendTemplate(
                        toTemplateMessage(candidate));
            } catch (RuntimeException exception) {
                stateService.markSendFailed(candidate.jobId());
                failed++;
                continue;
            }

            if (!stateService.markSubmitted(candidate.jobId(), result.messageId())) {
                throw new IllegalStateException(
                        "Could not persist Meta message id "
                                + result.messageId()
                                + " for notification job "
                                + candidate.jobId());
            }
            submitted++;
        }

        return new WhatsAppDispatchBatchResult(
                false,
                candidates.size(),
                submitted,
                cancelled,
                failed);
    }

    private boolean isCurrentlyEligible(LeadNotificationDispatchCandidate candidate) {
        VendorNotificationPreference preference = preferenceRepository
                .findByVendor_Id(candidate.vendorId())
                .orElse(null);
        return preference != null
                && preference.isWhatsAppLeadNotificationsEnabled()
                && !preference.isWhatsAppLeadNotificationsPaused()
                && preference.getWhatsAppConsentedAt() != null
                && preference.getWhatsAppConsentSource() != null
                && preference.getWhatsAppOptedOutAt() == null
                && Objects.equals(preference.getWhatsAppNumber(), candidate.destination())
                && LEAD_TEMPLATE_KEY.equals(candidate.templateKey());
    }

    private WhatsAppTemplateMessage toTemplateMessage(LeadNotificationDispatchCandidate candidate) {
        return new WhatsAppTemplateMessage(
                candidate.destination(),
                properties.getLeadTemplateName().trim(),
                properties.getLeadTemplateLanguage().trim(),
                List.of(
                        candidate.vendorName(),
                        candidate.serviceText(),
                        candidate.eventDateText(),
                        candidate.locationText(),
                        candidate.budgetText()));
    }
}
