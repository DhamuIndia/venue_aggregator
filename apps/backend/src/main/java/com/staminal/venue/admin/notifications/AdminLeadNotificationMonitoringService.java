package com.staminal.venue.admin.notifications;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.Attempt;
import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.ManualRetry;
import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.RequirementDetail;
import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.RequirementList;
import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.RequirementSummary;
import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.VendorDelivery;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
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
import com.staminal.venue.notifications.whatsapp.WhatsAppCloudApiProperties;
import com.staminal.venue.notifications.whatsapp.WhatsAppVendorEligibilityService;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.requirements.CustomerRequirementRepository;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminLeadNotificationMonitoringService {

    private final CustomerRequirementRepository requirementRepository;
    private final VendorLeadRepository vendorLeadRepository;
    private final LeadNotificationEvaluationRepository evaluationRepository;
    private final LeadNotificationJobRepository notificationJobRepository;
    private final WhatsAppNotificationAttemptRepository attemptRepository;
    private final WhatsAppVendorEligibilityService eligibilityService;
    private final WhatsAppCloudApiProperties whatsAppProperties;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public RequirementList getRequirements(Authentication authentication) {
        currentAdmin(authentication);
        List<RequirementSummary> content = requirementRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(requirement -> monitoringData(requirement, false).summary())
                .toList();
        return new RequirementList(
                content,
                whatsAppProperties.isSendingEnabled(),
                whatsAppProperties.getMaxAttempts());
    }

    @Transactional(readOnly = true)
    public RequirementDetail getRequirement(
            Long requirementId,
            Authentication authentication) {
        currentAdmin(authentication);
        CustomerRequirement requirement = requirementRepository.findById(requirementId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Requirement not found"));
        MonitoringData data = monitoringData(requirement, true);
        return new RequirementDetail(
                data.summary(),
                data.vendors(),
                whatsAppProperties.isSendingEnabled(),
                whatsAppProperties.getMaxAttempts());
    }

    public ManualRetry scheduleManualRetry(
            Long jobId,
            Authentication authentication) {
        User actor = currentAdmin(authentication);
        LeadNotificationJob job = notificationJobRepository
                .findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification job not found"));
        RetryEligibility eligibility = retryEligibility(job, Instant.now());
        if (!eligibility.allowed()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    eligibility.blockedReason());
        }

        Instant previousRetryAt = job.getNextRetryAt();
        Instant retryAt = Instant.now();
        job.setNextRetryAt(retryAt);
        notificationJobRepository.save(job);
        auditManualRetry(actor, job, previousRetryAt, retryAt);

        String message = whatsAppProperties.isSendingEnabled()
                ? "Retry scheduled for the next WhatsApp worker batch"
                : "Retry scheduled; it will wait until WhatsApp sending is enabled";
        return new ManualRetry(
                job.getId(),
                job.getStatus().name(),
                retryAt,
                job.getAttemptCount(),
                whatsAppProperties.getMaxAttempts(),
                whatsAppProperties.isSendingEnabled(),
                message);
    }

    private MonitoringData monitoringData(
            CustomerRequirement requirement,
            boolean includeVendors) {
        List<VendorLead> leads = vendorLeadRepository
                .findByRequirement_IdOrderByCreatedAtDesc(requirement.getId());
        Map<Long, LeadNotificationEvaluation> evaluationsByLead = evaluationRepository
                .findByRequirement_IdOrderByEvaluatedAtAsc(requirement.getId())
                .stream()
                .collect(Collectors.toMap(
                        evaluation -> evaluation.getVendorLead().getId(),
                        Function.identity(),
                        (first, second) -> second,
                        LinkedHashMap::new));
        Map<Long, LeadNotificationJob> jobsByLead = notificationJobRepository
                .findByRequirement_IdOrderByQueuedAtAsc(requirement.getId())
                .stream()
                .filter(job -> job.getChannel() == NotificationChannel.WHATSAPP)
                .filter(job -> job.getNotificationType() == LeadNotificationType.LEAD_MATCHED)
                .collect(Collectors.toMap(
                        job -> job.getVendorLead().getId(),
                        Function.identity(),
                        (first, second) -> second,
                        LinkedHashMap::new));

        RequirementSummary summary = toSummary(
                requirement,
                leads,
                evaluationsByLead,
                jobsByLead);
        if (!includeVendors) {
            return new MonitoringData(summary, List.of());
        }

        List<VendorDelivery> vendors = leads.stream()
                .sorted(Comparator.comparing(
                        lead -> vendorName(lead).toLowerCase(),
                        Comparator.naturalOrder()))
                .map(lead -> toVendorDelivery(
                        lead,
                        evaluationsByLead.get(lead.getId()),
                        jobsByLead.get(lead.getId())))
                .toList();
        return new MonitoringData(summary, vendors);
    }

    private RequirementSummary toSummary(
            CustomerRequirement requirement,
            List<VendorLead> leads,
            Map<Long, LeadNotificationEvaluation> evaluationsByLead,
            Map<Long, LeadNotificationJob> jobsByLead) {
        int subscribed = 0;
        int skipped = 0;
        int queued = 0;
        int sent = 0;
        int delivered = 0;
        int read = 0;
        int failed = 0;

        for (VendorLead lead : leads) {
            LeadNotificationEvaluation evaluation = evaluationsByLead.get(lead.getId());
            LeadNotificationJob job = jobsByLead.get(lead.getId());
            if (evaluation != null && evaluation.isSubscribedAtEvaluation()) {
                subscribed++;
            } else if (evaluation == null && job != null) {
                subscribed++;
            }
            if (isSkipped(evaluation, job)) {
                skipped++;
            }
            if (job == null) {
                continue;
            }
            if (job.getStatus() == LeadNotificationJobStatus.QUEUED
                    || job.getStatus() == LeadNotificationJobStatus.PROCESSING) {
                queued++;
            }
            if (job.getSentAt() != null) {
                sent++;
            }
            if (job.getDeliveredAt() != null) {
                delivered++;
            }
            if (job.getReadAt() != null) {
                read++;
            }
            if (job.getStatus() == LeadNotificationJobStatus.FAILED) {
                failed++;
            }
        }

        List<String> services = requirement.getServiceCategories()
                .stream()
                .map(category -> category.getCategoryName())
                .filter(value -> value != null && !value.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new RequirementSummary(
                requirement.getId(),
                requirement.getCustomer() == null
                        ? "VenueMart customer"
                        : firstText(requirement.getCustomer().getFullName(), "VenueMart customer"),
                requirement.getEventType(),
                requirement.getEventDate(),
                requirement.getLocation(),
                requirement.getCity(),
                requirement.getStatus() == null ? null : requirement.getStatus().name(),
                services,
                requirement.getCreatedAt(),
                leads.size(),
                subscribed,
                skipped,
                queued,
                sent,
                delivered,
                read,
                failed);
    }

    private VendorDelivery toVendorDelivery(
            VendorLead lead,
            LeadNotificationEvaluation evaluation,
            LeadNotificationJob job) {
        Long vendorId = lead.getVendor().getId();
        boolean currentlySubscribed = eligibilityService.isSubscribed(vendorId);
        boolean currentlyEligible = job == null
                ? eligibilityService.isEligibleForLeadNotifications(vendorId)
                : eligibilityService.isEligible(vendorId, job.getDestination());
        RetryEligibility retryEligibility = job == null
                ? RetryEligibility.deny("No WhatsApp notification job exists")
                : retryEligibility(job, Instant.now());
        List<Attempt> attempts = job == null
                ? List.of()
                : attemptRepository.findByJob_IdOrderByAttemptNumberAsc(job.getId())
                        .stream()
                        .map(this::toAttempt)
                        .toList();
        String skipReason = evaluation == null ? "Notification decision is unavailable" : evaluation.getSkipReason();
        if (job != null && job.getStatus() == LeadNotificationJobStatus.CANCELLED && !hasText(skipReason)) {
            skipReason = "Notification was cancelled because the vendor was no longer eligible before sending";
        }

        return new VendorDelivery(
                lead.getId(),
                lead.getPublicReference(),
                vendorId,
                vendorName(lead),
                lead.getService(),
                evaluation != null && evaluation.isSubscribedAtEvaluation(),
                currentlySubscribed,
                currentlyEligible,
                evaluation == null
                        ? LeadNotificationEvaluationOutcome.LEGACY_NOT_RECORDED.name()
                        : evaluation.getOutcome().name(),
                skipReason,
                evaluation == null ? null : evaluation.getEvaluatedAt(),
                job == null ? null : job.getId(),
                job == null ? "SKIPPED" : job.getStatus().name(),
                job == null ? null : maskDestination(job.getDestination()),
                job == null ? 0 : job.getAttemptCount(),
                job == null ? null : job.getQueuedAt(),
                job == null ? null : job.getSentAt(),
                job == null ? null : job.getDeliveredAt(),
                job == null ? null : job.getReadAt(),
                job == null ? null : job.getFailedAt(),
                job == null ? null : job.getNextRetryAt(),
                job == null ? null : job.getFailureCode(),
                job == null ? null : job.getFailureTitle(),
                job == null ? null : job.getFailureReason(),
                job == null ? null : job.getFailureTemporary(),
                retryEligibility.allowed(),
                retryEligibility.blockedReason(),
                attempts);
    }

    private Attempt toAttempt(WhatsAppNotificationAttempt attempt) {
        return new Attempt(
                attempt.getAttemptNumber(),
                attempt.getStatus().name(),
                attempt.getProviderMessageId(),
                attempt.getRequestedAt(),
                attempt.getSentAt(),
                attempt.getDeliveredAt(),
                attempt.getReadAt(),
                attempt.getFailedAt(),
                attempt.getCancelledAt(),
                attempt.getFailureCode(),
                attempt.getFailureTitle(),
                attempt.getFailureReason(),
                attempt.getFailureTemporary());
    }

    private RetryEligibility retryEligibility(
            LeadNotificationJob job,
            Instant now) {
        if (job.getStatus() != LeadNotificationJobStatus.FAILED) {
            return RetryEligibility.deny("Only failed notifications can be retried");
        }
        if (!Boolean.TRUE.equals(job.getFailureTemporary())) {
            return RetryEligibility.deny("Permanent failures cannot be retried");
        }
        if (job.getAttemptCount() >= whatsAppProperties.getMaxAttempts()) {
            return RetryEligibility.deny("Maximum retry attempts reached");
        }
        if (!eligibilityService.isEligible(job.getVendor().getId(), job.getDestination())) {
            return RetryEligibility.deny("Vendor is opted out, paused, unsubscribed, or changed the destination");
        }
        if (job.getNextRetryAt() != null && !job.getNextRetryAt().isAfter(now)) {
            return RetryEligibility.deny("Retry is already ready for dispatch");
        }
        return RetryEligibility.permit();
    }

    private boolean isSkipped(
            LeadNotificationEvaluation evaluation,
            LeadNotificationJob job) {
        return job == null
                || job.getStatus() == LeadNotificationJobStatus.CANCELLED
                || (evaluation != null
                        && evaluation.getOutcome() != LeadNotificationEvaluationOutcome.QUEUED);
    }

    private User currentAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin session");
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Admin user not found"));
        boolean isAdmin = actor.getRoles() != null && actor.getRoles()
                .stream()
                .map(Role::getName)
                .anyMatch(role -> role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN);
        if (!isAdmin || !"ACTIVE".equalsIgnoreCase(actor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active admin role is required");
        }
        return actor;
    }

    private void auditManualRetry(
            User actor,
            LeadNotificationJob job,
            Instant previousRetryAt,
            Instant retryAt) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("nextRetryAt", previousRetryAt);
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("nextRetryAt", retryAt);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requirementId", job.getRequirement().getId());
        metadata.put("vendorId", job.getVendor().getId());
        metadata.put("attemptCount", job.getAttemptCount());

        auditService.record(new AuditCommand(
                actor.getId(),
                primaryRole(actor),
                AuditAction.WHATSAPP_NOTIFICATION_MANUAL_RETRY_SCHEDULED,
                "LEAD_NOTIFICATION_JOB",
                String.valueOf(job.getId()),
                "Admin scheduled a WhatsApp notification retry",
                oldValues,
                newValues,
                metadata));
    }

    private String primaryRole(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .filter(role -> role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN)
                .max(Comparator.comparingInt(UserRole::ordinal))
                .orElse(UserRole.ADMIN)
                .name();
    }

    private String vendorName(VendorLead lead) {
        return firstText(
                lead.getVendor().getBusinessName(),
                lead.getVendor().getVendorName(),
                "VenueMart vendor");
    }

    private String maskDestination(String destination) {
        if (!hasText(destination)) {
            return null;
        }
        String digits = destination.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "••••";
        }
        return "••••" + digits.substring(digits.length() - 4);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record MonitoringData(
            RequirementSummary summary,
            List<VendorDelivery> vendors) {
    }

    private record RetryEligibility(
            boolean allowed,
            String blockedReason) {

        private static RetryEligibility permit() {
            return new RetryEligibility(true, null);
        }

        private static RetryEligibility deny(String reason) {
            return new RetryEligibility(false, reason);
        }
    }
}
