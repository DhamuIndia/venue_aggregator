package com.staminal.venue.overture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Separate, opt-in private storage; never reuse the public media bucket. */
@Component
public class OvertureDraftMediaProperties {
    private final boolean enabled;
    private final String bucket;

    public OvertureDraftMediaProperties(
            @Value("${app.overture-onboarding.media.enabled:false}") boolean enabled,
            @Value("${app.overture-onboarding.media.bucket:venue-draft-media-private}") String bucket) {
        this.enabled = enabled;
        this.bucket = bucket == null ? "" : bucket.strip();
    }

    public boolean isEnabled() { return enabled; }
    public String getBucket() { return bucket; }
}
