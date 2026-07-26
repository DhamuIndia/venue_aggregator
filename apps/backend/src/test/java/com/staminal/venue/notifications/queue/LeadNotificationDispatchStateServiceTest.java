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

    private LeadNotificationDispatchStateService service;

    @BeforeEach
    void setUp() {
        service = new LeadNotificationDispatchStateService(notificationJobRepository);
    }

    @Test
    void claimMovesQueuedJobToProcessingAndReturnsFrozenCandidate() {
        LeadNotificationJob job = queuedJob();
        when(notificationJobRepository.findForDispatch(
                eq(LeadNotificationJobStatus.QUEUED),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(job));
        when(notificationJobRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<LeadNotificationDispatchCandidate> candidates = service.claimQueued(20);

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.PROCESSING);
        assertThat(job.getProcessingStartedAt()).isNotNull();
        assertThat(candidates).containsExactly(new LeadNotificationDispatchCandidate(
                701L,
                501L,
                "+919884012346",
                "NEW_MATCHING_LEAD",
                "en",
                "Saffron Leaf Catering",
                "Photography",
                "12 September 2026",
                "Adyar, Chennai",
                "₹75,000–₹1,50,000"));
        verify(notificationJobRepository).saveAll(List.of(job));
    }

    @Test
    void successfulSubmissionStoresMetaMessageIdAndTimestamp() {
        LeadNotificationJob job = queuedJob();
        job.setStatus(LeadNotificationJobStatus.PROCESSING);
        when(notificationJobRepository.findByIdAndStatus(701L, LeadNotificationJobStatus.PROCESSING))
                .thenReturn(Optional.of(job));
        when(notificationJobRepository.save(any(LeadNotificationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean updated = service.markSubmitted(701L, "wamid.test-message-123");

        assertThat(updated).isTrue();
        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.SUBMITTED);
        assertThat(job.getProviderMessageId()).isEqualTo("wamid.test-message-123");
        assertThat(job.getSubmittedAt()).isNotNull();
    }

    @Test
    void failureDoesNotPutJobBackIntoAutomaticQueue() {
        LeadNotificationJob job = queuedJob();
        job.setStatus(LeadNotificationJobStatus.PROCESSING);
        when(notificationJobRepository.findByIdAndStatus(701L, LeadNotificationJobStatus.PROCESSING))
                .thenReturn(Optional.of(job));
        when(notificationJobRepository.save(any(LeadNotificationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.markSendFailed(701L);

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.SEND_FAILED);
        assertThat(job.getSendFailedAt()).isNotNull();
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
}
