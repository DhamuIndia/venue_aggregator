package com.staminal.venue.notifications.queue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeadNotificationDispatchStateService {

    private static final int MAX_BATCH_SIZE = 100;

    private final LeadNotificationJobRepository notificationJobRepository;
    private final WhatsAppNotificationAttemptRepository attemptRepository;

    @Transactional
    public List<LeadNotificationDispatchCandidate> claimReady(
            int requestedBatchSize,
            int maxAttempts,
            Set<Long> allowedVendorIds) {
        if (allowedVendorIds == null || allowedVendorIds.isEmpty()) {
            return List.of();
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        Instant now = Instant.now();
        List<LeadNotificationJob> jobs = notificationJobRepository.findReadyForDispatch(
                LeadNotificationJobStatus.QUEUED,
                LeadNotificationJobStatus.FAILED,
                now,
                maxAttempts,
                allowedVendorIds,
                PageRequest.of(0, batchSize));
        List<LeadNotificationDispatchCandidate> candidates = new ArrayList<>(jobs.size());

        for (LeadNotificationJob job : jobs) {
            int attemptNumber = job.getAttemptCount() + 1;
            job.setStatus(LeadNotificationJobStatus.PROCESSING);
            job.setProcessingStartedAt(now);
            job.setStatusUpdatedAt(now);
            job.setAttemptCount(attemptNumber);
            job.setNextRetryAt(null);
            job.setProviderMessageId(null);

            WhatsAppNotificationAttempt attempt = new WhatsAppNotificationAttempt();
            attempt.setJob(job);
            attempt.setAttemptNumber(attemptNumber);
            attempt.setStatus(WhatsAppNotificationAttemptStatus.PROCESSING);
            attempt.setRequestedAt(now);
            attempt.setStatusUpdatedAt(now);
            attempt = attemptRepository.save(attempt);

            candidates.add(toCandidate(job, attempt));
        }

        notificationJobRepository.saveAll(jobs);
        return List.copyOf(candidates);
    }

    @Transactional
    public boolean markSent(Long jobId, Long attemptId, String providerMessageId) {
        LeadNotificationJob job = findProcessing(jobId);
        WhatsAppNotificationAttempt attempt = findProcessingAttempt(attemptId);
        if (job == null || attempt == null || !job.getId().equals(attempt.getJob().getId())) {
            return false;
        }

        Instant now = Instant.now();
        String messageId = required(providerMessageId);
        job.setStatus(LeadNotificationJobStatus.SENT);
        job.setProviderMessageId(messageId);
        job.setSentAt(now);
        job.setSubmittedAt(now);
        job.setStatusUpdatedAt(now);
        job.setNextRetryAt(null);
        clearFailure(job);

        attempt.setStatus(WhatsAppNotificationAttemptStatus.SENT);
        attempt.setProviderMessageId(messageId);
        attempt.setSentAt(now);
        attempt.setStatusUpdatedAt(now);

        attemptRepository.save(attempt);
        notificationJobRepository.save(job);
        return true;
    }

    @Transactional
    public boolean markFailed(
            Long jobId,
            Long attemptId,
            LeadNotificationFailure failure,
            Instant nextRetryAt) {
        LeadNotificationJob job = findProcessing(jobId);
        WhatsAppNotificationAttempt attempt = findProcessingAttempt(attemptId);
        if (job == null || attempt == null || !job.getId().equals(attempt.getJob().getId())) {
            return false;
        }

        Instant now = Instant.now();
        job.setStatus(LeadNotificationJobStatus.FAILED);
        job.setFailedAt(now);
        job.setSendFailedAt(now);
        job.setStatusUpdatedAt(now);
        job.setNextRetryAt(failure.temporary() ? nextRetryAt : null);
        applyFailure(job, failure);

        attempt.setStatus(WhatsAppNotificationAttemptStatus.FAILED);
        attempt.setFailedAt(now);
        attempt.setStatusUpdatedAt(now);
        applyFailure(attempt, failure);

        attemptRepository.save(attempt);
        notificationJobRepository.save(job);
        return true;
    }

    @Transactional
    public boolean markCancelled(Long jobId, Long attemptId) {
        LeadNotificationJob job = findProcessing(jobId);
        WhatsAppNotificationAttempt attempt = findProcessingAttempt(attemptId);
        if (job == null || attempt == null || !job.getId().equals(attempt.getJob().getId())) {
            return false;
        }

        Instant now = Instant.now();
        job.setStatus(LeadNotificationJobStatus.CANCELLED);
        job.setCancelledAt(now);
        job.setStatusUpdatedAt(now);
        job.setNextRetryAt(null);
        attempt.setStatus(WhatsAppNotificationAttemptStatus.CANCELLED);
        attempt.setCancelledAt(now);
        attempt.setStatusUpdatedAt(now);

        attemptRepository.save(attempt);
        notificationJobRepository.save(job);
        return true;
    }

    private LeadNotificationJob findProcessing(Long jobId) {
        return notificationJobRepository
                .findByIdAndStatus(jobId, LeadNotificationJobStatus.PROCESSING)
                .orElse(null);
    }

    private WhatsAppNotificationAttempt findProcessingAttempt(Long attemptId) {
        return attemptRepository
                .findByIdAndStatus(attemptId, WhatsAppNotificationAttemptStatus.PROCESSING)
                .orElse(null);
    }

    private LeadNotificationDispatchCandidate toCandidate(
            LeadNotificationJob job,
            WhatsAppNotificationAttempt attempt) {
        return new LeadNotificationDispatchCandidate(
                job.getId(),
                attempt.getId(),
                attempt.getAttemptNumber(),
                job.getVendor().getId(),
                job.getDestination(),
                job.getTemplateKey(),
                job.getTemplateLanguage(),
                job.getLeadReference(),
                job.getVendorName(),
                job.getServiceText(),
                job.getEventDateText(),
                job.getLocationText(),
                job.getBudgetText());
    }

    private void applyFailure(LeadNotificationJob job, LeadNotificationFailure failure) {
        job.setFailureCode(failure.code());
        job.setFailureTitle(truncate(failure.title(), 255));
        job.setFailureReason(truncate(failure.reason(), 1000));
        job.setFailureTemporary(failure.temporary());
    }

    private void applyFailure(
            WhatsAppNotificationAttempt attempt,
            LeadNotificationFailure failure) {
        attempt.setFailureCode(failure.code());
        attempt.setFailureTitle(truncate(failure.title(), 255));
        attempt.setFailureReason(truncate(failure.reason(), 1000));
        attempt.setFailureTemporary(failure.temporary());
    }

    private void clearFailure(LeadNotificationJob job) {
        job.setFailureCode(null);
        job.setFailureTitle(null);
        job.setFailureReason(null);
        job.setFailureTemporary(null);
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Meta message id is required");
        }
        return value.trim();
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
