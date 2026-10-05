package com.staminal.venue.overture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OvertureOnboardingProperties {
    private final boolean enabled;
    private final String catalogPath;
    private final int maxBatchSize;

    public OvertureOnboardingProperties(
            @Value("${app.overture-onboarding.enabled:false}") boolean enabled,
            @Value("${app.overture-onboarding.catalog-path:}") String catalogPath,
            @Value("${app.overture-onboarding.max-batch-size:20}") int maxBatchSize) {
        this.enabled = enabled;
        this.catalogPath = catalogPath == null ? "" : catalogPath.trim();
        this.maxBatchSize = Math.clamp(maxBatchSize, 1, 20);
    }

    public boolean isEnabled() { return enabled; }
    public String getCatalogPath() { return catalogPath; }
    public int getMaxBatchSize() { return maxBatchSize; }
}
