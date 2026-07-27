package com.staminal.venue.notifications.queue;

import java.time.Instant;

import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.vendors.Entity.Vendors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "lead_notification_jobs")
public class LeadNotificationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_lead_id", nullable = false)
    private VendorLead vendorLead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendors vendor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private CustomerRequirement requirement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private LeadNotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LeadNotificationJobStatus status;

    @Column(nullable = false, length = 20)
    private String destination;

    @Column(name = "template_key", nullable = false, length = 80)
    private String templateKey;

    @Column(name = "template_language", nullable = false, length = 10)
    private String templateLanguage;

    @Column(name = "lead_reference", nullable = false, length = 25)
    private String leadReference;

    @Column(name = "vendor_name", nullable = false, length = 180)
    private String vendorName;

    @Column(name = "service_text", nullable = false, length = 500)
    private String serviceText;

    @Column(name = "event_type_text", nullable = false, length = 120)
    private String eventTypeText;

    @Column(name = "event_date_text", nullable = false, length = 80)
    private String eventDateText;

    @Column(name = "location_text", nullable = false, length = 255)
    private String locationText;

    @Column(name = "budget_text", nullable = false, length = 120)
    private String budgetText;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "send_failed_at")
    private Instant sendFailedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_code")
    private Integer failureCode;

    @Column(name = "failure_title", length = 255)
    private String failureTitle;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "failure_temporary")
    private Boolean failureTemporary;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (queuedAt == null) {
            queuedAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = LeadNotificationJobStatus.QUEUED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VendorLead getVendorLead() {
        return vendorLead;
    }

    public void setVendorLead(VendorLead vendorLead) {
        this.vendorLead = vendorLead;
    }

    public Vendors getVendor() {
        return vendor;
    }

    public void setVendor(Vendors vendor) {
        this.vendor = vendor;
    }

    public CustomerRequirement getRequirement() {
        return requirement;
    }

    public void setRequirement(CustomerRequirement requirement) {
        this.requirement = requirement;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public LeadNotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(LeadNotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public LeadNotificationJobStatus getStatus() {
        return status;
    }

    public void setStatus(LeadNotificationJobStatus status) {
        this.status = status;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public String getTemplateLanguage() {
        return templateLanguage;
    }

    public void setTemplateLanguage(String templateLanguage) {
        this.templateLanguage = templateLanguage;
    }

    public String getLeadReference() {
        return leadReference;
    }

    public void setLeadReference(String leadReference) {
        this.leadReference = leadReference;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getServiceText() {
        return serviceText;
    }

    public void setServiceText(String serviceText) {
        this.serviceText = serviceText;
    }

    public String getEventTypeText() {
        return eventTypeText;
    }

    public void setEventTypeText(String eventTypeText) {
        this.eventTypeText = eventTypeText;
    }

    public String getEventDateText() {
        return eventDateText;
    }

    public void setEventDateText(String eventDateText) {
        this.eventDateText = eventDateText;
    }

    public String getLocationText() {
        return locationText;
    }

    public void setLocationText(String locationText) {
        this.locationText = locationText;
    }

    public String getBudgetText() {
        return budgetText;
    }

    public void setBudgetText(String budgetText) {
        this.budgetText = budgetText;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public void setProcessingStartedAt(Instant processingStartedAt) {
        this.processingStartedAt = processingStartedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getSendFailedAt() {
        return sendFailedAt;
    }

    public void setSendFailedAt(Instant sendFailedAt) {
        this.sendFailedAt = sendFailedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public Integer getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(Integer failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureTitle() {
        return failureTitle;
    }

    public void setFailureTitle(String failureTitle) {
        this.failureTitle = failureTitle;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Boolean getFailureTemporary() {
        return failureTemporary;
    }

    public void setFailureTemporary(Boolean failureTemporary) {
        this.failureTemporary = failureTemporary;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void setStatusUpdatedAt(Instant statusUpdatedAt) {
        this.statusUpdatedAt = statusUpdatedAt;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(Instant queuedAt) {
        this.queuedAt = queuedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
