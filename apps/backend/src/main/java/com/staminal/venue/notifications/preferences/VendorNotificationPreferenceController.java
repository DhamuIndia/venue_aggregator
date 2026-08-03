package com.staminal.venue.notifications.preferences;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/vendor/notification-preferences")
@RequiredArgsConstructor
public class VendorNotificationPreferenceController {

    private final VendorNotificationPreferenceService preferenceService;

    @GetMapping
    public VendorNotificationPreferenceResponse getMyPreferences(Authentication authentication) {
        return preferenceService.getMyPreferences(authentication);
    }

    @PutMapping
    public VendorNotificationPreferenceResponse updateMyPreferences(
            @Valid @RequestBody UpdateVendorNotificationPreferenceRequest request,
            Authentication authentication) {
        return preferenceService.updateMyPreferences(request, authentication);
    }
}
