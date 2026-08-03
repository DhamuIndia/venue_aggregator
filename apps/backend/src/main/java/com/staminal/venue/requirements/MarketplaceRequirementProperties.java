package com.staminal.venue.requirements;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceRequirementProperties {

    private final boolean enabled;

    public MarketplaceRequirementProperties(
            @Value("${app.features.marketplace-requirements-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
