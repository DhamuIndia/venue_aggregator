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
