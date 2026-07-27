package com.staminal.venue.notifications.whatsapp;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staminal.venue.notifications.queue.LeadNotificationJob;
import com.staminal.venue.notifications.queue.LeadNotificationJobRepository;
import com.staminal.venue.notifications.queue.LeadNotificationJobStatus;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttempt;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttemptRepository;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttemptStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppDeliveryStateService {

    private final WhatsAppNotificationAttemptRepository attemptRepository;
    private final LeadNotificationJobRepository jobRepository;
    private final WhatsAppVendorEligibilityService eligibilityService;
    private final WhatsAppRetryPolicy retryPolicy;

    @Transactional
    public boolean apply(
            String providerMessageId,
            MetaMessageDeliveryStatus status,
            Instant providerTimestamp,
            MetaWebhookFailure failure) {
        WhatsAppNotificationAttempt attempt = attemptRepository
                .findByProviderMessageId(providerMessageId)
                .orElse(null);
        if (attempt == null) {
            return false;
        }

        LeadNotificationJob job = jobRepository
                .findByIdForUpdate(attempt.getJob().getId())
                .orElse(null);
        if (job == null) {
            return false;
        }

        Instant occurredAt = providerTimestamp == null ? Instant.now() : providerTimestamp;
        boolean currentAttempt = Objects.equals(job.getProviderMessageId(), providerMessageId);
        if (status == MetaMessageDeliveryStatus.FAILED) {
            applyFailure(attempt, job, currentAttempt, occurredAt, failure);
        } else {
            applySuccess(attempt, job, currentAttempt, status, occurredAt);
        }

        attemptRepository.save(attempt);
        jobRepository.save(job);
        return true;
    }

    private void applySuccess(
            WhatsAppNotificationAttempt attempt,
            LeadNotificationJob job,
            boolean currentAttempt,
            MetaMessageDeliveryStatus status,
            Instant occurredAt) {
        if (!shouldAdvance(attempt.getStatus(), status)) {
            return;
        }

        switch (status) {
            case SENT -> {
                attempt.setStatus(WhatsAppNotificationAttemptStatus.SENT);
                if (attempt.getSentAt() == null) {
                    attempt.setSentAt(occurredAt);
                }
            }
            case DELIVERED -> {
                attempt.setStatus(WhatsAppNotificationAttemptStatus.DELIVERED);
                attempt.setDeliveredAt(occurredAt);
            }
            case READ -> {
                attempt.setStatus(WhatsAppNotificationAttemptStatus.READ);
                attempt.setReadAt(occurredAt);
            }
            case FAILED -> throw new IllegalArgumentException("Failure is handled separately");
        }
        attempt.setStatusUpdatedAt(occurredAt);

        if (!currentAttempt) {
            return;
        }
        switch (status) {
            case SENT -> {
                job.setStatus(LeadNotificationJobStatus.SENT);
                if (job.getSentAt() == null) {
                    job.setSentAt(occurredAt);
                }
            }
            case DELIVERED -> {
                job.setStatus(LeadNotificationJobStatus.DELIVERED);
                job.setDeliveredAt(occurredAt);
            }
            case READ -> {
                job.setStatus(LeadNotificationJobStatus.READ);
                job.setReadAt(occurredAt);
            }
            case FAILED -> throw new IllegalArgumentException("Failure is handled separately");
        }
        job.setStatusUpdatedAt(occurredAt);
        job.setNextRetryAt(null);
        clearFailure(job);
    }

    private void applyFailure(
            WhatsAppNotificationAttempt attempt,
            LeadNotificationJob job,
            boolean currentAttempt,
            Instant occurredAt,
            MetaWebhookFailure failure) {
        if (attempt.getStatus() == WhatsAppNotificationAttemptStatus.DELIVERED
                || attempt.getStatus() == WhatsAppNotificationAttemptStatus.READ
                || attempt.getStatus() == WhatsAppNotificationAttemptStatus.FAILED) {
            return;
        }

        MetaWebhookFailure effectiveFailure = failure == null
                ? new MetaWebhookFailure(
                        null,
                        "Meta delivery failure",
                        "Meta reported that the WhatsApp message failed",
                        false)
                : failure;
        attempt.setStatus(WhatsAppNotificationAttemptStatus.FAILED);
        attempt.setFailedAt(occurredAt);
        attempt.setStatusUpdatedAt(occurredAt);
        applyFailure(attempt, effectiveFailure);

        if (!currentAttempt) {
            return;
        }

        job.setStatus(LeadNotificationJobStatus.FAILED);
        job.setFailedAt(occurredAt);
        job.setSendFailedAt(occurredAt);
        job.setStatusUpdatedAt(occurredAt);
        applyFailure(job, effectiveFailure);

        if (effectiveFailure.temporary()
                && eligibilityService.isEligible(
                        job.getVendor().getId(),
                        job.getDestination())) {
            job.setNextRetryAt(retryPolicy.nextRetryAt(job.getAttemptCount()));
        } else {
            job.setNextRetryAt(null);
        }
    }

    private boolean shouldAdvance(
            WhatsAppNotificationAttemptStatus current,
            MetaMessageDeliveryStatus incoming) {
        if (current == WhatsAppNotificationAttemptStatus.READ) {
            return false;
        }
        if (current == WhatsAppNotificationAttemptStatus.DELIVERED) {
            return incoming == MetaMessageDeliveryStatus.READ;
        }
        if (current == WhatsAppNotificationAttemptStatus.FAILED) {
            return incoming == MetaMessageDeliveryStatus.DELIVERED
                    || incoming == MetaMessageDeliveryStatus.READ;
        }
        return switch (incoming) {
            case SENT -> current != WhatsAppNotificationAttemptStatus.SENT;
            case DELIVERED, READ -> true;
            case FAILED -> false;
        };
    }

    private void applyFailure(
            WhatsAppNotificationAttempt attempt,
            MetaWebhookFailure failure) {
        attempt.setFailureCode(failure.code());
        attempt.setFailureTitle(truncate(failure.title(), 255));
        attempt.setFailureReason(truncate(failure.reason(), 1000));
        attempt.setFailureTemporary(failure.temporary());
    }

    private void applyFailure(LeadNotificationJob job, MetaWebhookFailure failure) {
        job.setFailureCode(failure.code());
        job.setFailureTitle(truncate(failure.title(), 255));
        job.setFailureReason(truncate(failure.reason(), 1000));
        job.setFailureTemporary(failure.temporary());
    }

    private void clearFailure(LeadNotificationJob job) {
        job.setFailureCode(null);
        job.setFailureTitle(null);
        job.setFailureReason(null);
        job.setFailureTemporary(null);
    }

    private String truncate(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximumLength
                ? trimmed
                : trimmed.substring(0, maximumLength);
    }
}
