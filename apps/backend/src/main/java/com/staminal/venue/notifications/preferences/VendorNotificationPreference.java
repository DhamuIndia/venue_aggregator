package com.staminal.venue.notifications.preferences;

import java.time.Instant;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "vendor_notification_preferences")
public class VendorNotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false, unique = true)
    private Vendors vendor;

    @Column(name = "whatsapp_lead_notifications_enabled", nullable = false)
    private boolean whatsAppLeadNotificationsEnabled;

    @Column(name = "whatsapp_lead_notifications_paused", nullable = false)
    private boolean whatsAppLeadNotificationsPaused;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsAppNumber;

    @Column(name = "whatsapp_consented_at")
    private Instant whatsAppConsentedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "whatsapp_consent_source", length = 40)
    private WhatsAppConsentSource whatsAppConsentSource;

    @Column(name = "whatsapp_opted_out_at")
    private Instant whatsAppOptedOutAt;

    @Column(name = "whatsapp_paused_at")
    private Instant whatsAppPausedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
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

    public Vendors getVendor() {
        return vendor;
    }

    public void setVendor(Vendors vendor) {
        this.vendor = vendor;
    }

    public boolean isWhatsAppLeadNotificationsEnabled() {
        return whatsAppLeadNotificationsEnabled;
    }

    public void setWhatsAppLeadNotificationsEnabled(boolean whatsAppLeadNotificationsEnabled) {
        this.whatsAppLeadNotificationsEnabled = whatsAppLeadNotificationsEnabled;
    }

    public boolean isWhatsAppLeadNotificationsPaused() {
        return whatsAppLeadNotificationsPaused;
    }

    public void setWhatsAppLeadNotificationsPaused(boolean whatsAppLeadNotificationsPaused) {
        this.whatsAppLeadNotificationsPaused = whatsAppLeadNotificationsPaused;
    }

    public String getWhatsAppNumber() {
        return whatsAppNumber;
    }

    public void setWhatsAppNumber(String whatsAppNumber) {
        this.whatsAppNumber = whatsAppNumber;
    }

    public Instant getWhatsAppConsentedAt() {
        return whatsAppConsentedAt;
    }

    public void setWhatsAppConsentedAt(Instant whatsAppConsentedAt) {
        this.whatsAppConsentedAt = whatsAppConsentedAt;
    }

    public WhatsAppConsentSource getWhatsAppConsentSource() {
        return whatsAppConsentSource;
    }

    public void setWhatsAppConsentSource(WhatsAppConsentSource whatsAppConsentSource) {
        this.whatsAppConsentSource = whatsAppConsentSource;
    }

    public Instant getWhatsAppOptedOutAt() {
        return whatsAppOptedOutAt;
    }

    public void setWhatsAppOptedOutAt(Instant whatsAppOptedOutAt) {
        this.whatsAppOptedOutAt = whatsAppOptedOutAt;
    }

    public Instant getWhatsAppPausedAt() {
        return whatsAppPausedAt;
    }

    public void setWhatsAppPausedAt(Instant whatsAppPausedAt) {
        this.whatsAppPausedAt = whatsAppPausedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
