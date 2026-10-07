package com.staminal.venue.overture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class OverturePublicationProperties {
    private final boolean enabled;
    private final boolean licensedPhotosEnabled;
    @Autowired
    public OverturePublicationProperties(@Value("${app.overture-onboarding.publication.enabled:false}") boolean enabled,
            @Value("${app.overture-onboarding.publication.licensed-photos-enabled:false}") boolean licensedPhotosEnabled) {
        this.enabled = enabled;
        this.licensedPhotosEnabled = licensedPhotosEnabled;
    }
    public OverturePublicationProperties(boolean enabled) { this(enabled, false); }
    public boolean isEnabled() { return enabled; }
    public boolean isLicensedPhotosEnabled() { return licensedPhotosEnabled; }
}
