package com.staminal.venue.notifications.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.staminal.venue.vendors.Entity.Vendors;

@ExtendWith(MockitoExtension.class)
class LeadNotificationDispatchStateServiceTest {

    @Mock
    private LeadNotificationJobRepository notificationJobRepository;

    @Mock
    private WhatsAppNotificationAttemptRepository attemptRepository;

    private LeadNotificationDispatchStateService service;

    @BeforeEach
    void setUp() {
        service = new LeadNotificationDispatchStateService(
                notificationJobRepository,
                attemptRepository);
    }

    @Test
    void claimCreatesOneDurableAttemptAndMovesJobToProcessing() {
        LeadNotificationJob job = queuedJob();
        when(notificationJobRepository.findReadyForDispatch(
                eq(LeadNotificationJobStatus.QUEUED),
                eq(LeadNotificationJobStatus.FAILED),
                any(Instant.class),
                eq(3),
                any(Pageable.class)))
                .thenReturn(List.of(job));
        when(attemptRepository.save(any(WhatsAppNotificationAttempt.class)))
                .thenAnswer(invocation -> {
                    WhatsAppNotificationAttempt attempt = invocation.getArgument(0);
                    attempt.setId(801L);
                    return attempt;
                });
        when(notificationJobRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<LeadNotificationDispatchCandidate> candidates = service.claimReady(20, 3);

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.PROCESSING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getProcessingStartedAt()).isNotNull();
        assertThat(candidates).containsExactly(new LeadNotificationDispatchCandidate(
                701L,
                801L,
                1,
                501L,
                "+919884012346",
                "NEW_MATCHING_LEAD",
                "en",
                "Saffron Leaf Catering",
                "Photography",
                "12 September 2026",
                "Adyar, Chennai",
                "₹75,000–₹1,50,000"));
        verify(attemptRepository).save(any(WhatsAppNotificationAttempt.class));
    }

    @Test
    void successfulSubmissionStoresMetaMessageIdAsSentOnJobAndAttempt() {
        LeadNotificationJob job = processingJob();
        WhatsAppNotificationAttempt attempt = processingAttempt(job);
        when(notificationJobRepository.findByIdAndStatus(
                701L,
                LeadNotificationJobStatus.PROCESSING))
                .thenReturn(Optional.of(job));
        when(attemptRepository.findByIdAndStatus(
                801L,
                WhatsAppNotificationAttemptStatus.PROCESSING))
                .thenReturn(Optional.of(attempt));

        boolean updated = service.markSent(701L, 801L, "wamid.test-message-123");

        assertThat(updated).isTrue();
        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.SENT);
        assertThat(job.getProviderMessageId()).isEqualTo("wamid.test-message-123");
        assertThat(job.getSentAt()).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(WhatsAppNotificationAttemptStatus.SENT);
        assertThat(attempt.getProviderMessageId()).isEqualTo("wamid.test-message-123");
    }

    @Test
    void retryClaimCreatesNextAttemptAndDetachesOldMessageIdFromCurrentJob() {
        LeadNotificationJob job = queuedJob();
        job.setStatus(LeadNotificationJobStatus.FAILED);
        job.setAttemptCount(1);
        job.setProviderMessageId("wamid.old-attempt");
        job.setFailureTemporary(true);
        job.setNextRetryAt(Instant.parse("2026-07-26T07:01:00Z"));
        when(notificationJobRepository.findReadyForDispatch(
                eq(LeadNotificationJobStatus.QUEUED),
                eq(LeadNotificationJobStatus.FAILED),
                any(Instant.class),
                eq(3),
                any(Pageable.class)))
                .thenReturn(List.of(job));
        when(attemptRepository.save(any(WhatsAppNotificationAttempt.class)))
                .thenAnswer(invocation -> {
                    WhatsAppNotificationAttempt attempt = invocation.getArgument(0);
                    attempt.setId(802L);
                    return attempt;
                });

        List<LeadNotificationDispatchCandidate> candidates = service.claimReady(20, 3);

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.PROCESSING);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getProviderMessageId()).isNull();
        assertThat(job.getNextRetryAt()).isNull();
        assertThat(candidates.getFirst().attemptId()).isEqualTo(802L);
        assertThat(candidates.getFirst().attemptNumber()).isEqualTo(2);
    }

    @Test
    void temporaryFailureIsFailedAndGetsOnlyTheSuppliedRetryTime() {
        LeadNotificationJob job = processingJob();
        WhatsAppNotificationAttempt attempt = processingAttempt(job);
        Instant retryAt = Instant.parse("2026-07-26T08:01:00Z");
        when(notificationJobRepository.findByIdAndStatus(
                701L,
                LeadNotificationJobStatus.PROCESSING))
                .thenReturn(Optional.of(job));
        when(attemptRepository.findByIdAndStatus(
                801L,
                WhatsAppNotificationAttemptStatus.PROCESSING))
                .thenReturn(Optional.of(attempt));

        service.markFailed(
                701L,
                801L,
                new LeadNotificationFailure(
                        130429,
                        "Rate limited",
                        "Cloud API throughput reached",
                        true),
                retryAt);

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(130429);
        assertThat(job.getNextRetryAt()).isEqualTo(retryAt);
        assertThat(attempt.getStatus()).isEqualTo(WhatsAppNotificationAttemptStatus.FAILED);
    }

    @Test
    void permanentFailureNeverKeepsARetryTime() {
        LeadNotificationJob job = processingJob();
        WhatsAppNotificationAttempt attempt = processingAttempt(job);
        when(notificationJobRepository.findByIdAndStatus(
                701L,
                LeadNotificationJobStatus.PROCESSING))
                .thenReturn(Optional.of(job));
        when(attemptRepository.findByIdAndStatus(
                801L,
                WhatsAppNotificationAttemptStatus.PROCESSING))
                .thenReturn(Optional.of(attempt));

        service.markFailed(
                701L,
                801L,
                new LeadNotificationFailure(131026, "Undeliverable", "Not reachable", false),
                Instant.parse("2026-07-26T08:01:00Z"));

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.FAILED);
        assertThat(job.getNextRetryAt()).isNull();
    }

    private LeadNotificationJob queuedJob() {
        Vendors vendor = new Vendors();
        vendor.setId(501L);

        LeadNotificationJob job = new LeadNotificationJob();
        job.setId(701L);
        job.setVendor(vendor);
        job.setStatus(LeadNotificationJobStatus.QUEUED);
        job.setDestination("+919884012346");
        job.setTemplateKey("NEW_MATCHING_LEAD");
        job.setTemplateLanguage("en");
        job.setVendorName("Saffron Leaf Catering");
        job.setServiceText("Photography");
        job.setEventDateText("12 September 2026");
        job.setLocationText("Adyar, Chennai");
        job.setBudgetText("₹75,000–₹1,50,000");
        job.setQueuedAt(Instant.parse("2026-07-26T07:00:00Z"));
        return job;
    }

    private LeadNotificationJob processingJob() {
        LeadNotificationJob job = queuedJob();
        job.setStatus(LeadNotificationJobStatus.PROCESSING);
        job.setAttemptCount(1);
        return job;
    }

    private WhatsAppNotificationAttempt processingAttempt(LeadNotificationJob job) {
        WhatsAppNotificationAttempt attempt = new WhatsAppNotificationAttempt();
        attempt.setId(801L);
        attempt.setJob(job);
        attempt.setAttemptNumber(1);
        attempt.setStatus(WhatsAppNotificationAttemptStatus.PROCESSING);
        return attempt;
    }
}
