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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "lead_notification_evaluations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lead_notification_evaluations_delivery",
                columnNames = {"vendor_lead_id", "channel", "notification_type"}))
public class LeadNotificationEvaluation {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_job_id")
    private LeadNotificationJob notificationJob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private LeadNotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private LeadNotificationEvaluationOutcome outcome;

    @Column(name = "subscribed_at_evaluation", nullable = false)
    private boolean subscribedAtEvaluation;

    @Column(name = "skip_reason", length = 500)
    private String skipReason;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (evaluatedAt == null) {
            evaluatedAt = now;
        }
        updatedAt = now;
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

    public LeadNotificationJob getNotificationJob() {
        return notificationJob;
    }

    public void setNotificationJob(LeadNotificationJob notificationJob) {
        this.notificationJob = notificationJob;
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

    public LeadNotificationEvaluationOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(LeadNotificationEvaluationOutcome outcome) {
        this.outcome = outcome;
    }

    public boolean isSubscribedAtEvaluation() {
        return subscribedAtEvaluation;
    }

    public void setSubscribedAtEvaluation(boolean subscribedAtEvaluation) {
        this.subscribedAtEvaluation = subscribedAtEvaluation;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
