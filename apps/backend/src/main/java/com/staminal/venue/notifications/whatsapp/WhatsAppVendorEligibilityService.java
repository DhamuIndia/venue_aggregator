package com.staminal.venue.notifications.whatsapp;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.staminal.venue.notifications.preferences.VendorNotificationPreference;
import com.staminal.venue.notifications.preferences.VendorNotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppVendorEligibilityService {

    private final VendorNotificationPreferenceRepository preferenceRepository;

    public boolean isSubscribed(Long vendorId) {
        VendorNotificationPreference preference = preferenceRepository
                .findByVendor_Id(vendorId)
                .orElse(null);
        return hasActiveSubscription(preference);
    }

    public boolean isEligibleForLeadNotifications(Long vendorId) {
        VendorNotificationPreference preference = preferenceRepository
                .findByVendor_Id(vendorId)
                .orElse(null);
        return hasActiveSubscription(preference)
                && !preference.isWhatsAppLeadNotificationsPaused();
    }

    public boolean isEligible(Long vendorId, String destination) {
        VendorNotificationPreference preference = preferenceRepository
                .findByVendor_Id(vendorId)
                .orElse(null);
        return hasActiveSubscription(preference)
                && !preference.isWhatsAppLeadNotificationsPaused()
                && Objects.equals(preference.getWhatsAppNumber(), destination);
    }

    private boolean hasActiveSubscription(VendorNotificationPreference preference) {
        return preference != null
                && preference.isWhatsAppLeadNotificationsEnabled()
                && preference.getWhatsAppNumber() != null
                && !preference.getWhatsAppNumber().isBlank()
                && preference.getWhatsAppConsentedAt() != null
                && preference.getWhatsAppConsentSource() != null
                && preference.getWhatsAppOptedOutAt() == null;
    }
}
