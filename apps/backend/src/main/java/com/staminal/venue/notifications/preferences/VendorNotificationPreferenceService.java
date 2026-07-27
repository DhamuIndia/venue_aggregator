package com.staminal.venue.notifications.preferences;

import java.time.Instant;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorNotificationPreferenceService {

    private final VendorNotificationPreferenceRepository preferenceRepository;
    private final VendorRepository vendorRepository;

    @Transactional(readOnly = true)
    public VendorNotificationPreferenceResponse getMyPreferences(Authentication authentication) {
        Vendors vendor = currentVendor(authentication);
        return preferenceRepository.findByVendor_Id(vendor.getId())
                .map(this::toResponse)
                .orElseGet(() -> defaultResponse(vendor));
    }

    @Transactional
    public VendorNotificationPreferenceResponse updateMyPreferences(
            UpdateVendorNotificationPreferenceRequest request,
            Authentication authentication) {

        Vendors vendor = currentVendor(authentication);
        VendorNotificationPreference preference = preferenceRepository.findByVendor_Id(vendor.getId())
                .orElseGet(() -> newPreference(vendor));

        boolean enabled = Boolean.TRUE.equals(request.whatsAppLeadNotificationsEnabled());
        boolean paused = Boolean.TRUE.equals(request.whatsAppLeadNotificationsPaused());

        if (paused && !enabled) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "WhatsApp lead notifications must be enabled before they can be paused");
        }

        String requestedNumber = trimToNull(request.whatsAppNumber());
        String effectiveNumber = requestedNumber == null
                ? preference.getWhatsAppNumber()
                : requestedNumber;
        if (enabled && effectiveNumber == null) {
            effectiveNumber = firstText(
                    vendor.getWhatsAppNumber(),
                    vendor.getContactNumber(),
                    vendor.getUser() == null ? null : vendor.getUser().getPhone());
        }

        if (effectiveNumber != null && (enabled || requestedNumber != null)) {
            effectiveNumber = normalizeWhatsAppNumber(effectiveNumber);
        }

        Instant now = Instant.now();
        boolean phoneChanged = !Objects.equals(preference.getWhatsAppNumber(), effectiveNumber);
        boolean consentRequired = enabled
                && (!preference.isWhatsAppLeadNotificationsEnabled()
                        || preference.getWhatsAppConsentedAt() == null
                        || preference.getWhatsAppConsentSource() == null
                        || phoneChanged);

        if (enabled && effectiveNumber == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A WhatsApp number is required to enable lead notifications");
        }

        if (consentRequired && !Boolean.TRUE.equals(request.consentConfirmed())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Explicit WhatsApp lead notification consent is required");
        }

        preference.setWhatsAppNumber(effectiveNumber);

        if (enabled) {
            boolean wasPaused = preference.isWhatsAppLeadNotificationsPaused();
            preference.setWhatsAppLeadNotificationsEnabled(true);
            preference.setWhatsAppOptedOutAt(null);

            if (consentRequired) {
                preference.setWhatsAppConsentedAt(now);
                preference.setWhatsAppConsentSource(WhatsAppConsentSource.VENDOR_SETTINGS);
            }

            preference.setWhatsAppLeadNotificationsPaused(paused);
            if (paused && !wasPaused) {
                preference.setWhatsAppPausedAt(now);
            } else if (!paused) {
                preference.setWhatsAppPausedAt(null);
            }
        } else {
            boolean shouldRecordOptOut = preference.isWhatsAppLeadNotificationsEnabled()
                    || (preference.getWhatsAppConsentedAt() != null
                            && preference.getWhatsAppOptedOutAt() == null);
            preference.setWhatsAppLeadNotificationsEnabled(false);
            preference.setWhatsAppLeadNotificationsPaused(false);
            preference.setWhatsAppPausedAt(null);
            if (shouldRecordOptOut) {
                preference.setWhatsAppOptedOutAt(now);
            }
        }

        preference.setUpdatedAt(now);
        return toResponse(preferenceRepository.save(preference));
    }

    private VendorNotificationPreference newPreference(Vendors vendor) {
        VendorNotificationPreference preference = new VendorNotificationPreference();
        preference.setVendor(vendor);
        return preference;
    }

    private Vendors currentVendor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session", exception);
        }

        return vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor profile not found"));
    }

    private VendorNotificationPreferenceResponse defaultResponse(Vendors vendor) {
        return new VendorNotificationPreferenceResponse(
                vendor.getId(),
                false,
                false,
                false,
                firstText(
                        vendor.getWhatsAppNumber(),
                        vendor.getContactNumber(),
                        vendor.getUser() == null ? null : vendor.getUser().getPhone()),
                null,
                null,
                null,
                null,
                null);
    }

    private VendorNotificationPreferenceResponse toResponse(VendorNotificationPreference preference) {
        boolean canReceive = preference.isWhatsAppLeadNotificationsEnabled()
                && !preference.isWhatsAppLeadNotificationsPaused();
        return new VendorNotificationPreferenceResponse(
                preference.getVendor().getId(),
                preference.isWhatsAppLeadNotificationsEnabled(),
                preference.isWhatsAppLeadNotificationsPaused(),
                canReceive,
                preference.getWhatsAppNumber(),
                preference.getWhatsAppConsentedAt(),
                preference.getWhatsAppConsentSource(),
                preference.getWhatsAppOptedOutAt(),
                preference.getWhatsAppPausedAt(),
                preference.getUpdatedAt());
    }

    private String normalizeWhatsAppNumber(String value) {
        String compact = value.trim().replaceAll("[\\s()\\-]", "");

        if (compact.matches("^\\+[1-9]\\d{7,14}$")) {
            return compact;
        }
        if (compact.matches("^[6-9]\\d{9}$")) {
            return "+91" + compact;
        }
        if (compact.matches("^0[6-9]\\d{9}$")) {
            return "+91" + compact.substring(1);
        }
        if (compact.matches("^91[6-9]\\d{9}$")) {
            return "+" + compact;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Enter the WhatsApp number with a valid country code");
    }

    private String firstText(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
