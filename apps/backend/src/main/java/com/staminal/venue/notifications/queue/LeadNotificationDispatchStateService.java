package com.staminal.venue.notifications.queue;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeadNotificationDispatchStateService {

    private static final int MAX_BATCH_SIZE = 100;

    private final LeadNotificationJobRepository notificationJobRepository;

    @Transactional
    public List<LeadNotificationDispatchCandidate> claimQueued(int requestedBatchSize) {
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        List<LeadNotificationJob> jobs = notificationJobRepository.findForDispatch(
                LeadNotificationJobStatus.QUEUED,
                PageRequest.of(0, batchSize));
        Instant now = Instant.now();
        jobs.forEach(job -> {
            job.setStatus(LeadNotificationJobStatus.PROCESSING);
            job.setProcessingStartedAt(now);
        });
        notificationJobRepository.saveAll(jobs);
        return jobs.stream().map(this::toCandidate).toList();
    }

    @Transactional
    public boolean markSubmitted(Long jobId, String providerMessageId) {
        LeadNotificationJob job = findProcessing(jobId);
        if (job == null) {
            return false;
        }
        job.setStatus(LeadNotificationJobStatus.SUBMITTED);
        job.setProviderMessageId(required(providerMessageId));
        job.setSubmittedAt(Instant.now());
        notificationJobRepository.save(job);
        return true;
    }

    @Transactional
    public boolean markSendFailed(Long jobId) {
        LeadNotificationJob job = findProcessing(jobId);
        if (job == null) {
            return false;
        }
        job.setStatus(LeadNotificationJobStatus.SEND_FAILED);
        job.setSendFailedAt(Instant.now());
        notificationJobRepository.save(job);
        return true;
    }

    @Transactional
    public boolean markCancelled(Long jobId) {
        LeadNotificationJob job = findProcessing(jobId);
        if (job == null) {
            return false;
        }
        job.setStatus(LeadNotificationJobStatus.CANCELLED);
        job.setCancelledAt(Instant.now());
        notificationJobRepository.save(job);
        return true;
    }

    private LeadNotificationJob findProcessing(Long jobId) {
        return notificationJobRepository
                .findByIdAndStatus(jobId, LeadNotificationJobStatus.PROCESSING)
                .orElse(null);
    }

    private LeadNotificationDispatchCandidate toCandidate(LeadNotificationJob job) {
        return new LeadNotificationDispatchCandidate(
                job.getId(),
                job.getVendor().getId(),
                job.getDestination(),
                job.getTemplateKey(),
                job.getTemplateLanguage(),
                job.getVendorName(),
                job.getServiceText(),
                job.getEventDateText(),
                job.getLocationText(),
                job.getBudgetText());
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Meta message id is required");
        }
        return value.trim();
    }
}
