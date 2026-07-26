package com.staminal.venue.notifications.whatsapp;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.leads.LeadReference;
import com.staminal.venue.notifications.queue.LeadNotificationDispatchCandidate;
import com.staminal.venue.notifications.queue.LeadNotificationDispatchStateService;
import com.staminal.venue.notifications.queue.LeadNotificationFailure;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppNotificationDispatcher {

    private static final String LEAD_TEMPLATE_KEY = "NEW_MATCHING_LEAD";

    private final WhatsAppCloudApiProperties properties;
    private final LeadNotificationDispatchStateService stateService;
    private final WhatsAppVendorEligibilityService eligibilityService;
    private final WhatsAppRetryPolicy retryPolicy;
    private final WhatsAppCloudApiClient cloudApiClient;

    public WhatsAppDispatchBatchResult dispatchQueuedBatch() {
        if (!properties.isSendingEnabled()) {
            return WhatsAppDispatchBatchResult.disabled();
        }

        properties.validateForSending();
        List<LeadNotificationDispatchCandidate> candidates =
                stateService.claimReady(
                        properties.getBatchSize(),
                        properties.getMaxAttempts());
        int sent = 0;
        int cancelled = 0;
        int failed = 0;

        for (LeadNotificationDispatchCandidate candidate : candidates) {
            if (!isCurrentlyEligible(candidate)) {
                stateService.markCancelled(candidate.jobId(), candidate.attemptId());
                cancelled++;
                continue;
            }

            WhatsAppCloudApiSendResult result;
            try {
                result = cloudApiClient.sendTemplate(
                        toTemplateMessage(candidate));
            } catch (WhatsAppCloudApiException exception) {
                boolean canRetry = exception.isTemporary()
                        && exception.isRetrySafe()
                        && isCurrentlyEligible(candidate);
                Instant nextRetryAt = canRetry
                        ? retryPolicy.nextRetryAt(candidate.attemptNumber())
                        : null;
                stateService.markFailed(
                        candidate.jobId(),
                        candidate.attemptId(),
                        new LeadNotificationFailure(
                                exception.getProviderCode(),
                                exception.getProviderTitle(),
                                exception.getMessage(),
                                exception.isTemporary()),
                        nextRetryAt);
                failed++;
                continue;
            } catch (RuntimeException exception) {
                stateService.markFailed(
                        candidate.jobId(),
                        candidate.attemptId(),
                        new LeadNotificationFailure(
                                null,
                                "Submission result unknown",
                                exception.getMessage(),
                                false),
                        null);
                failed++;
                continue;
            }

            if (!stateService.markSent(
                    candidate.jobId(),
                    candidate.attemptId(),
                    result.messageId())) {
                throw new IllegalStateException(
                        "Could not persist Meta message id "
                                + result.messageId()
                                + " for notification job "
                                + candidate.jobId());
            }
            sent++;
        }

        return new WhatsAppDispatchBatchResult(
                false,
                candidates.size(),
                sent,
                cancelled,
                failed);
    }

    private boolean isCurrentlyEligible(LeadNotificationDispatchCandidate candidate) {
        return eligibilityService.isEligible(candidate.vendorId(), candidate.destination())
                && LEAD_TEMPLATE_KEY.equals(candidate.templateKey())
                && LeadReference.isValid(candidate.leadReference());
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
                        candidate.budgetText()),
                candidate.leadReference());
    }
}
