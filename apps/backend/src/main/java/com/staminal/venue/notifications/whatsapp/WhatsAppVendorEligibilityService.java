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

    public boolean isEligible(Long vendorId, String destination) {
        VendorNotificationPreference preference = preferenceRepository
                .findByVendor_Id(vendorId)
                .orElse(null);
        return preference != null
                && preference.isWhatsAppLeadNotificationsEnabled()
                && !preference.isWhatsAppLeadNotificationsPaused()
                && preference.getWhatsAppConsentedAt() != null
                && preference.getWhatsAppConsentSource() != null
                && preference.getWhatsAppOptedOutAt() == null
                && Objects.equals(preference.getWhatsAppNumber(), destination);
    }
}
