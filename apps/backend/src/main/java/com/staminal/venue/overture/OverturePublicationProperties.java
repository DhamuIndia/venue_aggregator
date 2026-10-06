package com.staminal.venue.overture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class OverturePublicationProperties {
    private final boolean enabled;
    public OverturePublicationProperties(@Value("${app.overture-onboarding.publication.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }
    public boolean isEnabled() { return enabled; }
}
