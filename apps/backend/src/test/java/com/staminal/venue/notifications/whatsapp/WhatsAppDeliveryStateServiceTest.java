package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.staminal.venue.notifications.queue.LeadNotificationJob;
import com.staminal.venue.notifications.queue.LeadNotificationJobRepository;
import com.staminal.venue.notifications.queue.LeadNotificationJobStatus;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttempt;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttemptRepository;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttemptStatus;
import com.staminal.venue.vendors.Entity.Vendors;

@ExtendWith(MockitoExtension.class)
class WhatsAppDeliveryStateServiceTest {

    @Mock
    private WhatsAppNotificationAttemptRepository attemptRepository;

    @Mock
    private LeadNotificationJobRepository jobRepository;

    @Mock
    private WhatsAppVendorEligibilityService eligibilityService;

    @Mock
    private WhatsAppRetryPolicy retryPolicy;

    private WhatsAppDeliveryStateService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppDeliveryStateService(
                attemptRepository,
                jobRepository,
                eligibilityService,
                retryPolicy);
    }

    @Test
    void advancesSentMessageToDeliveredThenReadWithoutRegressing() {
        LeadNotificationJob job = currentJob("wamid.current");
        WhatsAppNotificationAttempt attempt = currentAttempt(job, "wamid.current");
        givenAttempt(attempt, job);
        Instant deliveredAt = Instant.parse("2026-07-26T08:00:00Z");

        service.apply(
                "wamid.current",
                MetaMessageDeliveryStatus.DELIVERED,
                deliveredAt,
                null);

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.DELIVERED);
        assertThat(job.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(attempt.getStatus())
                .isEqualTo(WhatsAppNotificationAttemptStatus.DELIVERED);

        Instant readAt = Instant.parse("2026-07-26T08:01:00Z");
        service.apply(
                "wamid.current",
                MetaMessageDeliveryStatus.READ,
                readAt,
                null);
        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.READ);
        assertThat(job.getReadAt()).isEqualTo(readAt);
    }

    @Test
    void duplicateAndOutOfOrderSentEventCannotRegressDeliveredMessage() {
        LeadNotificationJob job = currentJob("wamid.current");
        job.setStatus(LeadNotificationJobStatus.DELIVERED);
        WhatsAppNotificationAttempt attempt = currentAttempt(job, "wamid.current");
        attempt.setStatus(WhatsAppNotificationAttemptStatus.DELIVERED);
        givenAttempt(attempt, job);

        service.apply(
                "wamid.current",
                MetaMessageDeliveryStatus.SENT,
                Instant.parse("2026-07-26T07:59:00Z"),
                null);

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.DELIVERED);
        assertThat(attempt.getStatus())
                .isEqualTo(WhatsAppNotificationAttemptStatus.DELIVERED);
    }

    @Test
    void temporaryFailureSchedulesRetryOnlyWhileVendorRemainsEligible() {
        LeadNotificationJob job = currentJob("wamid.current");
        WhatsAppNotificationAttempt attempt = currentAttempt(job, "wamid.current");
        givenAttempt(attempt, job);
        Instant retryAt = Instant.parse("2026-07-26T08:02:00Z");
        when(eligibilityService.isEligible(501L, "+919884012346")).thenReturn(true);
        when(retryPolicy.nextRetryAt(1)).thenReturn(retryAt);

        service.apply(
                "wamid.current",
                MetaMessageDeliveryStatus.FAILED,
                Instant.parse("2026-07-26T08:00:00Z"),
                new MetaWebhookFailure(
                        130429,
                        "Rate limit",
                        "Cloud API throughput reached",
                        true));

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.FAILED);
        assertThat(job.getFailureCode()).isEqualTo(130429);
        assertThat(job.getFailureReason()).isEqualTo("Cloud API throughput reached");
        assertThat(job.getNextRetryAt()).isEqualTo(retryAt);
        assertThat(attempt.getStatus()).isEqualTo(WhatsAppNotificationAttemptStatus.FAILED);
    }

    @Test
    void optedOutVendorNeverGetsRetryForTemporaryFailure() {
        LeadNotificationJob job = currentJob("wamid.current");
        WhatsAppNotificationAttempt attempt = currentAttempt(job, "wamid.current");
        givenAttempt(attempt, job);
        when(eligibilityService.isEligible(501L, "+919884012346")).thenReturn(false);

        service.apply(
                "wamid.current",
                MetaMessageDeliveryStatus.FAILED,
                Instant.parse("2026-07-26T08:00:00Z"),
                new MetaWebhookFailure(130429, "Rate limit", "Try later", true));

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.FAILED);
        assertThat(job.getNextRetryAt()).isNull();
        verify(retryPolicy, never()).nextRetryAt(any(Integer.class));
    }

    @Test
    void permanentFailureNeverGetsRetry() {
        LeadNotificationJob job = currentJob("wamid.current");
        WhatsAppNotificationAttempt attempt = currentAttempt(job, "wamid.current");
        givenAttempt(attempt, job);

        service.apply(
                "wamid.current",
                MetaMessageDeliveryStatus.FAILED,
                Instant.parse("2026-07-26T08:00:00Z"),
                new MetaWebhookFailure(131026, "Undeliverable", "Not reachable", false));

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.FAILED);
        assertThat(job.getNextRetryAt()).isNull();
        verify(eligibilityService, never()).isEligible(any(), any());
        verify(retryPolicy, never()).nextRetryAt(any(Integer.class));
    }

    @Test
    void lateWebhookFromOlderAttemptCannotChangeCurrentRetryAttempt() {
        LeadNotificationJob job = currentJob("wamid.new");
        job.setAttemptCount(2);
        WhatsAppNotificationAttempt oldAttempt = currentAttempt(job, "wamid.old");
        oldAttempt.setAttemptNumber(1);
        givenAttempt(oldAttempt, job);

        service.apply(
                "wamid.old",
                MetaMessageDeliveryStatus.FAILED,
                Instant.parse("2026-07-26T08:00:00Z"),
                new MetaWebhookFailure(130429, "Rate limit", "Old failure", true));

        assertThat(oldAttempt.getStatus()).isEqualTo(WhatsAppNotificationAttemptStatus.FAILED);
        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.SENT);
        assertThat(job.getProviderMessageId()).isEqualTo("wamid.new");
        assertThat(job.getNextRetryAt()).isNull();
        verify(retryPolicy, never()).nextRetryAt(any(Integer.class));
    }

    @Test
    void failureCannotOverrideDeliveredOrReadSuccess() {
        LeadNotificationJob job = currentJob("wamid.current");
        job.setStatus(LeadNotificationJobStatus.DELIVERED);
        WhatsAppNotificationAttempt attempt = currentAttempt(job, "wamid.current");
        attempt.setStatus(WhatsAppNotificationAttemptStatus.DELIVERED);
        givenAttempt(attempt, job);

        service.apply(
                "wamid.current",
                MetaMessageDeliveryStatus.FAILED,
                Instant.parse("2026-07-26T08:02:00Z"),
                new MetaWebhookFailure(131000, "Failure", "Late failure", true));

        assertThat(job.getStatus()).isEqualTo(LeadNotificationJobStatus.DELIVERED);
        assertThat(attempt.getStatus())
                .isEqualTo(WhatsAppNotificationAttemptStatus.DELIVERED);
        verify(retryPolicy, never()).nextRetryAt(any(Integer.class));
    }

    @Test
    void duplicateFailedWebhookCannotPostponeExistingRetry() {
        LeadNotificationJob job = currentJob("wamid.current");
        job.setStatus(LeadNotificationJobStatus.FAILED);
        Instant existingRetry = Instant.parse("2026-07-26T08:02:00Z");
        job.setNextRetryAt(existingRetry);
        WhatsAppNotificationAttempt attempt = currentAttempt(job, "wamid.current");
        attempt.setStatus(WhatsAppNotificationAttemptStatus.FAILED);
        givenAttempt(attempt, job);

        service.apply(
                "wamid.current",
                MetaMessageDeliveryStatus.FAILED,
                Instant.parse("2026-07-26T08:01:00Z"),
                new MetaWebhookFailure(130429, "Rate limit", "Duplicate", true));

        assertThat(job.getNextRetryAt()).isEqualTo(existingRetry);
        verify(eligibilityService, never()).isEligible(any(), any());
        verify(retryPolicy, never()).nextRetryAt(any(Integer.class));
    }

    private void givenAttempt(
            WhatsAppNotificationAttempt attempt,
            LeadNotificationJob job) {
        when(attemptRepository.findByProviderMessageId(attempt.getProviderMessageId()))
                .thenReturn(Optional.of(attempt));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
    }

    private LeadNotificationJob currentJob(String providerMessageId) {
        Vendors vendor = new Vendors();
        vendor.setId(501L);

        LeadNotificationJob job = new LeadNotificationJob();
        job.setId(701L);
        job.setVendor(vendor);
        job.setDestination("+919884012346");
        job.setStatus(LeadNotificationJobStatus.SENT);
        job.setProviderMessageId(providerMessageId);
        job.setAttemptCount(1);
        return job;
    }

    private WhatsAppNotificationAttempt currentAttempt(
            LeadNotificationJob job,
            String providerMessageId) {
        WhatsAppNotificationAttempt attempt = new WhatsAppNotificationAttempt();
        attempt.setId(801L);
        attempt.setJob(job);
        attempt.setAttemptNumber(1);
        attempt.setProviderMessageId(providerMessageId);
        attempt.setStatus(WhatsAppNotificationAttemptStatus.SENT);
        return attempt;
    }
}
