package com.staminal.venue.notifications.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class VendorNotificationPreferenceServiceTest {

    @Mock
    private VendorNotificationPreferenceRepository preferenceRepository;

    @Mock
    private VendorRepository vendorRepository;

    private VendorNotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new VendorNotificationPreferenceService(preferenceRepository, vendorRepository);
    }

    @Test
    void existingVendorDefaultsToUnsubscribedWithoutCreatingPreferenceRow() {
        Vendors vendor = vendor();
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.empty());

        VendorNotificationPreferenceResponse response = service.getMyPreferences(authentication());

        assertThat(response.vendorId()).isEqualTo(501L);
        assertThat(response.whatsAppLeadNotificationsEnabled()).isFalse();
        assertThat(response.whatsAppLeadNotificationsPaused()).isFalse();
        assertThat(response.canReceiveWhatsAppLeadNotifications()).isFalse();
        assertThat(response.whatsAppNumber()).isEqualTo("9884012346");
        assertThat(response.whatsAppConsentedAt()).isNull();
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void explicitConsentEnablesLeadAlertsAndNormalizesIndianNumber() {
        Vendors vendor = vendor();
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(VendorNotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VendorNotificationPreferenceResponse response = service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(true, false, "98840 12346", true),
                authentication());

        ArgumentCaptor<VendorNotificationPreference> preferenceCaptor =
                ArgumentCaptor.forClass(VendorNotificationPreference.class);
        verify(preferenceRepository).save(preferenceCaptor.capture());
        VendorNotificationPreference saved = preferenceCaptor.getValue();

        assertThat(saved.getVendor()).isSameAs(vendor);
        assertThat(saved.isWhatsAppLeadNotificationsEnabled()).isTrue();
        assertThat(saved.isWhatsAppLeadNotificationsPaused()).isFalse();
        assertThat(saved.getWhatsAppNumber()).isEqualTo("+919884012346");
        assertThat(saved.getWhatsAppConsentedAt()).isNotNull();
        assertThat(saved.getWhatsAppConsentSource()).isEqualTo(WhatsAppConsentSource.VENDOR_SETTINGS);
        assertThat(saved.getWhatsAppOptedOutAt()).isNull();
        assertThat(response.canReceiveWhatsAppLeadNotifications()).isTrue();
    }

    @Test
    void enablingFirstTimeRejectsMissingConsentConfirmation() {
        Vendors vendor = vendor();
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(true, false, "9884012346", false),
                authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("Explicit WhatsApp");
                });

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void activeVendorCanPauseAndResumeWithoutRenewingConsent() {
        Vendors vendor = vendor();
        VendorNotificationPreference preference = subscribedPreference(vendor);
        Instant originalConsent = preference.getWhatsAppConsentedAt();

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(VendorNotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VendorNotificationPreferenceResponse paused = service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(true, true, null, null),
                authentication());

        assertThat(paused.whatsAppLeadNotificationsEnabled()).isTrue();
        assertThat(paused.whatsAppLeadNotificationsPaused()).isTrue();
        assertThat(paused.canReceiveWhatsAppLeadNotifications()).isFalse();
        assertThat(paused.whatsAppPausedAt()).isNotNull();
        assertThat(paused.whatsAppConsentedAt()).isEqualTo(originalConsent);

        VendorNotificationPreferenceResponse resumed = service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(true, false, null, null),
                authentication());

        assertThat(resumed.whatsAppLeadNotificationsPaused()).isFalse();
        assertThat(resumed.canReceiveWhatsAppLeadNotifications()).isTrue();
        assertThat(resumed.whatsAppPausedAt()).isNull();
        assertThat(resumed.whatsAppConsentedAt()).isEqualTo(originalConsent);
    }

    @Test
    void disablingRecordsOptOutAndClearsPause() {
        Vendors vendor = vendor();
        VendorNotificationPreference preference = subscribedPreference(vendor);
        preference.setWhatsAppLeadNotificationsPaused(true);
        preference.setWhatsAppPausedAt(Instant.parse("2026-07-20T08:00:00Z"));

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(VendorNotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VendorNotificationPreferenceResponse response = service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(false, false, null, null),
                authentication());

        assertThat(response.whatsAppLeadNotificationsEnabled()).isFalse();
        assertThat(response.whatsAppLeadNotificationsPaused()).isFalse();
        assertThat(response.canReceiveWhatsAppLeadNotifications()).isFalse();
        assertThat(response.whatsAppPausedAt()).isNull();
        assertThat(response.whatsAppOptedOutAt()).isNotNull();
        assertThat(response.whatsAppConsentedAt()).isNotNull();
    }

    @Test
    void changingSubscribedNumberRequiresFreshConsent() {
        Vendors vendor = vendor();
        VendorNotificationPreference preference = subscribedPreference(vendor);

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));

        assertThatThrownBy(() -> service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(true, false, "+919999999999", null),
                authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void pauseCannotBeSelectedWhileNotificationsAreDisabled() {
        Vendors vendor = vendor();
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(false, true, null, null),
                authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void remainingDisabledDoesNotRequireOrCopyLegacyProfilePhone() {
        Vendors vendor = vendor();
        vendor.setWhatsAppNumber("not-complete");
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(VendorNotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VendorNotificationPreferenceResponse response = service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(false, false, null, null),
                authentication());

        assertThat(response.whatsAppLeadNotificationsEnabled()).isFalse();
        assertThat(response.whatsAppNumber()).isNull();
        assertThat(response.whatsAppOptedOutAt()).isNull();
    }

    @Test
    void repeatedDisabledSavePreservesOriginalOptOutTime() {
        Vendors vendor = vendor();
        VendorNotificationPreference preference = subscribedPreference(vendor);
        preference.setWhatsAppLeadNotificationsEnabled(false);
        Instant originalOptOut = Instant.parse("2026-07-21T08:00:00Z");
        preference.setWhatsAppOptedOutAt(originalOptOut);

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(preferenceRepository.findByVendor_Id(501L)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(VendorNotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VendorNotificationPreferenceResponse response = service.updateMyPreferences(
                new UpdateVendorNotificationPreferenceRequest(false, false, null, null),
                authentication());

        assertThat(response.whatsAppOptedOutAt()).isEqualTo(originalOptOut);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
    }

    private Vendors vendor() {
        User user = new User();
        user.setId(301L);
        user.setPhone("9884012345");

        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setUser(user);
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setWhatsAppNumber("9884012346");
        return vendor;
    }

    private VendorNotificationPreference subscribedPreference(Vendors vendor) {
        VendorNotificationPreference preference = new VendorNotificationPreference();
        preference.setId(701L);
        preference.setVendor(vendor);
        preference.setWhatsAppLeadNotificationsEnabled(true);
        preference.setWhatsAppLeadNotificationsPaused(false);
        preference.setWhatsAppNumber("+919884012346");
        preference.setWhatsAppConsentedAt(Instant.parse("2026-07-20T07:00:00Z"));
        preference.setWhatsAppConsentSource(WhatsAppConsentSource.VENDOR_SETTINGS);
        preference.setUpdatedAt(Instant.parse("2026-07-20T07:00:00Z"));
        return preference;
    }
}
