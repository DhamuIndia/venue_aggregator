package com.staminal.venue.admin.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.RequirementDetail;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.queue.LeadNotificationEvaluation;
import com.staminal.venue.notifications.queue.LeadNotificationEvaluationOutcome;
import com.staminal.venue.notifications.queue.LeadNotificationEvaluationRepository;
import com.staminal.venue.notifications.queue.LeadNotificationJob;
import com.staminal.venue.notifications.queue.LeadNotificationJobRepository;
import com.staminal.venue.notifications.queue.LeadNotificationJobStatus;
import com.staminal.venue.notifications.queue.LeadNotificationType;
import com.staminal.venue.notifications.queue.NotificationChannel;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttempt;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttemptRepository;
import com.staminal.venue.notifications.queue.WhatsAppNotificationAttemptStatus;
import com.staminal.venue.notifications.whatsapp.WhatsAppCloudApiProperties;
import com.staminal.venue.notifications.whatsapp.WhatsAppVendorEligibilityService;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.requirements.CustomerRequirementRepository;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;

@ExtendWith(MockitoExtension.class)
class AdminLeadNotificationMonitoringServiceTest {

    @Mock
    private CustomerRequirementRepository requirementRepository;
    @Mock
    private VendorLeadRepository vendorLeadRepository;
    @Mock
    private LeadNotificationEvaluationRepository evaluationRepository;
    @Mock
    private LeadNotificationJobRepository notificationJobRepository;
    @Mock
    private WhatsAppNotificationAttemptRepository attemptRepository;
    @Mock
    private WhatsAppVendorEligibilityService eligibilityService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private WhatsAppCloudApiProperties properties;
    private AdminLeadNotificationMonitoringService service;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppCloudApiProperties();
        properties.setMaxAttempts(3);
        properties.setRolloutAllowedVendorIds(Set.of(501L, 502L, 503L));
        service = new AdminLeadNotificationMonitoringService(
                requirementRepository,
                vendorLeadRepository,
                evaluationRepository,
                notificationJobRepository,
                attemptRepository,
                eligibilityService,
                properties,
                userRepository,
                auditService);
    }

    @Test
    void aggregatesMatchedSubscribedSkippedAndDeliveryFunnelWithHistory() {
        CustomerRequirement requirement = requirement();
        VendorLead readLead = lead(901L, "LEAD-0123456789ABCDEF0123", 501L, "Read Vendor");
        VendorLead failedLead = lead(902L, "LEAD-123456789ABCDEF01234", 502L, "Failed Vendor");
        VendorLead skippedLead = lead(903L, "LEAD-23456789ABCDEF012345", 503L, "Skipped Vendor");
        readLead.setRequirement(requirement);
        failedLead.setRequirement(requirement);
        skippedLead.setRequirement(requirement);

        LeadNotificationJob readJob = job(601L, readLead, LeadNotificationJobStatus.READ);
        readJob.setSentAt(Instant.parse("2026-07-26T08:00:00Z"));
        readJob.setDeliveredAt(Instant.parse("2026-07-26T08:01:00Z"));
        readJob.setReadAt(Instant.parse("2026-07-26T08:02:00Z"));

        LeadNotificationJob failedJob = job(602L, failedLead, LeadNotificationJobStatus.FAILED);
        failedJob.setFailureTemporary(true);
        failedJob.setFailureTitle("Rate limited");
        failedJob.setFailureReason("Cloud API throughput reached");
        failedJob.setAttemptCount(1);
        failedJob.setFailedAt(Instant.parse("2026-07-26T08:03:00Z"));

        LeadNotificationEvaluation readEvaluation = evaluation(readLead, readJob, true,
                LeadNotificationEvaluationOutcome.QUEUED, null);
        LeadNotificationEvaluation failedEvaluation = evaluation(failedLead, failedJob, true,
                LeadNotificationEvaluationOutcome.QUEUED, null);
        LeadNotificationEvaluation skippedEvaluation = evaluation(skippedLead, null, false,
                LeadNotificationEvaluationOutcome.SKIPPED_NOT_SUBSCRIBED,
                "Vendor has not subscribed");
        WhatsAppNotificationAttempt failedAttempt = attempt(failedJob);

        when(userRepository.findById(301L)).thenReturn(Optional.of(admin()));
        when(requirementRepository.findById(801L)).thenReturn(Optional.of(requirement));
        when(vendorLeadRepository.findByRequirement_IdOrderByCreatedAtDesc(801L))
                .thenReturn(List.of(readLead, failedLead, skippedLead));
        when(evaluationRepository.findByRequirement_IdOrderByEvaluatedAtAsc(801L))
                .thenReturn(List.of(readEvaluation, failedEvaluation, skippedEvaluation));
        when(notificationJobRepository.findByRequirement_IdOrderByQueuedAtAsc(801L))
                .thenReturn(List.of(readJob, failedJob));
        when(eligibilityService.isSubscribed(any())).thenReturn(true);
        when(eligibilityService.isEligible(any(), any())).thenReturn(true);
        when(eligibilityService.isEligibleForLeadNotifications(any())).thenReturn(false);
        when(attemptRepository.findByJob_IdOrderByAttemptNumberAsc(601L)).thenReturn(List.of());
        when(attemptRepository.findByJob_IdOrderByAttemptNumberAsc(602L))
                .thenReturn(List.of(failedAttempt));

        RequirementDetail response = service.getRequirement(801L, adminAuth());

        assertThat(response.summary().matchedVendorCount()).isEqualTo(3);
        assertThat(response.summary().subscribedVendorCount()).isEqualTo(2);
        assertThat(response.summary().notificationSkippedCount()).isEqualTo(1);
        assertThat(response.summary().sentCount()).isEqualTo(1);
        assertThat(response.summary().deliveredCount()).isEqualTo(1);
        assertThat(response.summary().readCount()).isEqualTo(1);
        assertThat(response.summary().failedCount()).isEqualTo(1);
        assertThat(response.rolloutAllowedVendorIds()).containsExactly(501L, 502L, 503L);
        assertThat(response.vendors()).hasSize(3);
        assertThat(response.vendors())
                .filteredOn(vendor -> vendor.notificationJobId() != null
                        && vendor.notificationJobId().equals(602L))
                .singleElement()
                .satisfies(vendor -> {
                    assertThat(vendor.failureReason()).isEqualTo("Cloud API throughput reached");
                    assertThat(vendor.rolloutAllowed()).isTrue();
                    assertThat(vendor.canManualRetry()).isTrue();
                    assertThat(vendor.retryHistory()).hasSize(1);
                });
    }

    @Test
    void manualRetrySchedulesOnlyTemporaryEligibleFailureAndAuditsIt() {
        LeadNotificationJob failedJob = job(
                602L,
                lead(902L, "LEAD-123456789ABCDEF01234", 502L, "Failed Vendor"),
                LeadNotificationJobStatus.FAILED);
        failedJob.setFailureTemporary(true);
        failedJob.setAttemptCount(1);
        User admin = admin();

        when(userRepository.findById(301L)).thenReturn(Optional.of(admin));
        when(notificationJobRepository.findByIdForUpdate(602L))
                .thenReturn(Optional.of(failedJob));
        when(eligibilityService.isEligible(502L, "+919884012346")).thenReturn(true);

        var response = service.scheduleManualRetry(602L, adminAuth());

        assertThat(response.nextRetryAt()).isNotNull();
        assertThat(response.sendingEnabled()).isFalse();
        assertThat(response.message()).contains("wait until WhatsApp sending is enabled");
        assertThat(failedJob.getStatus()).isEqualTo(LeadNotificationJobStatus.FAILED);
        assertThat(failedJob.getNextRetryAt()).isEqualTo(response.nextRetryAt());
        verify(notificationJobRepository).save(failedJob);

        ArgumentCaptor<AuditCommand> auditCaptor = ArgumentCaptor.forClass(AuditCommand.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action())
                .isEqualTo(AuditAction.WHATSAPP_NOTIFICATION_MANUAL_RETRY_SCHEDULED);
        assertThat(auditCaptor.getValue().entityId()).isEqualTo("602");
    }

    @Test
    void manualRetryRejectsPermanentFailure() {
        LeadNotificationJob failedJob = job(
                602L,
                lead(902L, "LEAD-123456789ABCDEF01234", 502L, "Failed Vendor"),
                LeadNotificationJobStatus.FAILED);
        failedJob.setFailureTemporary(false);
        failedJob.setAttemptCount(1);

        when(userRepository.findById(301L)).thenReturn(Optional.of(admin()));
        when(notificationJobRepository.findByIdForUpdate(602L))
                .thenReturn(Optional.of(failedJob));

        assertThatThrownBy(() -> service.scheduleManualRetry(602L, adminAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("Permanent failures cannot be retried");
                });
        verify(notificationJobRepository, never()).save(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void manualRetryRejectsOptedOutOrOtherwiseIneligibleVendor() {
        LeadNotificationJob failedJob = job(
                602L,
                lead(902L, "LEAD-123456789ABCDEF01234", 502L, "Failed Vendor"),
                LeadNotificationJobStatus.FAILED);
        failedJob.setFailureTemporary(true);
        failedJob.setAttemptCount(1);

        when(userRepository.findById(301L)).thenReturn(Optional.of(admin()));
        when(notificationJobRepository.findByIdForUpdate(602L))
                .thenReturn(Optional.of(failedJob));
        when(eligibilityService.isEligible(502L, "+919884012346")).thenReturn(false);

        assertThatThrownBy(() -> service.scheduleManualRetry(602L, adminAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("opted out");
                });
        verify(notificationJobRepository, never()).save(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void manualRetryRejectsVendorOutsideControlledRollout() {
        properties.setRolloutAllowedVendorIds(Set.of(501L));
        LeadNotificationJob failedJob = job(
                602L,
                lead(902L, "LEAD-123456789ABCDEF01234", 502L, "Failed Vendor"),
                LeadNotificationJobStatus.FAILED);
        failedJob.setFailureTemporary(true);
        failedJob.setAttemptCount(1);

        when(userRepository.findById(301L)).thenReturn(Optional.of(admin()));
        when(notificationJobRepository.findByIdForUpdate(602L))
                .thenReturn(Optional.of(failedJob));

        assertThatThrownBy(() -> service.scheduleManualRetry(602L, adminAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("outside the controlled rollout");
                });
        verify(notificationJobRepository, never()).save(any());
        verify(auditService, never()).record(any());
    }

    private CustomerRequirement requirement() {
        User customer = new User();
        customer.setId(101L);
        customer.setFullName("Priya Raman");

        VendorCategory category = new VendorCategory();
        category.setId(1L);
        category.setCategoryName("Photography");

        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(801L);
        requirement.setCustomer(customer);
        requirement.setEventType("Wedding");
        requirement.setEventDate(LocalDate.parse("2026-09-12"));
        requirement.setLocation("Adyar");
        requirement.setCity("Chennai");
        requirement.setStatus(CustomerRequirementStatus.OPEN);
        requirement.setServiceCategories(Set.of(category));
        requirement.setCreatedAt(Instant.parse("2026-07-26T07:00:00Z"));
        return requirement;
    }

    private VendorLead lead(Long id, String reference, Long vendorId, String vendorName) {
        Vendors vendor = new Vendors();
        vendor.setId(vendorId);
        vendor.setBusinessName(vendorName);

        VendorLead lead = new VendorLead();
        lead.setId(id);
        lead.setPublicReference(reference);
        lead.setVendor(vendor);
        lead.setService("Photography");
        return lead;
    }

    private LeadNotificationJob job(
            Long id,
            VendorLead lead,
            LeadNotificationJobStatus status) {
        LeadNotificationJob job = new LeadNotificationJob();
        job.setId(id);
        job.setVendorLead(lead);
        job.setVendor(lead.getVendor());
        job.setRequirement(lead.getRequirement() == null ? requirement() : lead.getRequirement());
        job.setChannel(NotificationChannel.WHATSAPP);
        job.setNotificationType(LeadNotificationType.LEAD_MATCHED);
        job.setStatus(status);
        job.setDestination("+919884012346");
        job.setQueuedAt(Instant.parse("2026-07-26T07:01:00Z"));
        return job;
    }

    private LeadNotificationEvaluation evaluation(
            VendorLead lead,
            LeadNotificationJob job,
            boolean subscribed,
            LeadNotificationEvaluationOutcome outcome,
            String skipReason) {
        LeadNotificationEvaluation evaluation = new LeadNotificationEvaluation();
        evaluation.setVendorLead(lead);
        evaluation.setVendor(lead.getVendor());
        evaluation.setRequirement(lead.getRequirement());
        evaluation.setNotificationJob(job);
        evaluation.setChannel(NotificationChannel.WHATSAPP);
        evaluation.setNotificationType(LeadNotificationType.LEAD_MATCHED);
        evaluation.setOutcome(outcome);
        evaluation.setSubscribedAtEvaluation(subscribed);
        evaluation.setSkipReason(skipReason);
        evaluation.setEvaluatedAt(Instant.parse("2026-07-26T07:01:00Z"));
        return evaluation;
    }

    private WhatsAppNotificationAttempt attempt(LeadNotificationJob job) {
        WhatsAppNotificationAttempt attempt = new WhatsAppNotificationAttempt();
        attempt.setJob(job);
        attempt.setAttemptNumber(1);
        attempt.setStatus(WhatsAppNotificationAttemptStatus.FAILED);
        attempt.setRequestedAt(Instant.parse("2026-07-26T08:00:00Z"));
        attempt.setFailedAt(Instant.parse("2026-07-26T08:03:00Z"));
        attempt.setFailureReason("Cloud API throughput reached");
        attempt.setFailureTemporary(true);
        return attempt;
    }

    private User admin() {
        Role role = new Role();
        role.setName(UserRole.ADMIN);
        User user = new User();
        user.setId(301L);
        user.setFullName("VenueMart Admin");
        user.setStatus("ACTIVE");
        user.setRoles(Set.of(role));
        return user;
    }

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
