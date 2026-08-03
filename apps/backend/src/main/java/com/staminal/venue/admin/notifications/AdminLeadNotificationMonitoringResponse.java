package com.staminal.venue.admin.notifications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminLeadNotificationMonitoringResponse {

    private AdminLeadNotificationMonitoringResponse() {
    }

    public record RequirementList(
            List<RequirementSummary> content,
            boolean sendingEnabled,
            int maxAttempts,
            List<Long> rolloutAllowedVendorIds) {
    }

    public record RequirementSummary(
            Long requirementId,
            String customerName,
            String eventType,
            LocalDate eventDate,
            String location,
            String city,
            String requirementStatus,
            List<String> services,
            Instant createdAt,
            int matchedVendorCount,
            int subscribedVendorCount,
            int notificationSkippedCount,
            int queuedCount,
            int sentCount,
            int deliveredCount,
            int readCount,
            int failedCount) {
    }

    public record RequirementDetail(
            RequirementSummary summary,
            List<VendorDelivery> vendors,
            boolean sendingEnabled,
            int maxAttempts,
            List<Long> rolloutAllowedVendorIds) {
    }

    public record VendorDelivery(
            Long vendorLeadId,
            String leadReference,
            Long vendorId,
            String vendorName,
            String service,
            boolean subscribedAtEvaluation,
            boolean currentlySubscribed,
            boolean currentlyEligible,
            boolean rolloutAllowed,
            String evaluationOutcome,
            String skipReason,
            Instant evaluatedAt,
            Long notificationJobId,
            String notificationStatus,
            String maskedDestination,
            int attemptCount,
            Instant queuedAt,
            Instant sentAt,
            Instant deliveredAt,
            Instant readAt,
            Instant failedAt,
            Instant nextRetryAt,
            Integer failureCode,
            String failureTitle,
            String failureReason,
            Boolean failureTemporary,
            boolean canManualRetry,
            String manualRetryBlockedReason,
            List<Attempt> retryHistory) {
    }

    public record Attempt(
            int attemptNumber,
            String status,
            String providerMessageId,
            Instant requestedAt,
            Instant sentAt,
            Instant deliveredAt,
            Instant readAt,
            Instant failedAt,
            Instant cancelledAt,
            Integer failureCode,
            String failureTitle,
            String failureReason,
            Boolean failureTemporary) {
    }

    public record ManualRetry(
            Long notificationJobId,
            String status,
            Instant nextRetryAt,
            int attemptCount,
            int maxAttempts,
            boolean sendingEnabled,
            String message) {
    }
}
