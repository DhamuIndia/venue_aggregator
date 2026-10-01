package com.staminal.venue.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VenueDiscoveryProperties {
    private final boolean enabled;
    private final boolean liveApiEnabled;
    private final String googleApiKey;
    private final int maxResults;
    private final int dailyRequestLimit;
    private final int timeoutSeconds;

    public VenueDiscoveryProperties(
            @Value("${app.features.venue-discovery-enabled:false}") boolean enabled,
            @Value("${app.venue-discovery.live-api-enabled:false}") boolean liveApiEnabled,
            @Value("${app.venue-discovery.google-api-key:}") String googleApiKey,
            @Value("${app.venue-discovery.max-results:10}") int maxResults,
            @Value("${app.venue-discovery.daily-request-limit:100}") int dailyRequestLimit,
            @Value("${app.venue-discovery.timeout-seconds:10}") int timeoutSeconds) {
        this.enabled = enabled;
        this.liveApiEnabled = liveApiEnabled;
        this.googleApiKey = googleApiKey == null ? "" : googleApiKey.trim();
        this.maxResults = Math.clamp(maxResults, 1, 20);
        this.dailyRequestLimit = Math.clamp(dailyRequestLimit, 0, 10000);
        this.timeoutSeconds = Math.clamp(timeoutSeconds, 1, 30);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isLiveApiEnabled() { return liveApiEnabled; }
    public String getGoogleApiKey() { return googleApiKey; }
    public int getMaxResults() { return maxResults; }
    public int getDailyRequestLimit() { return dailyRequestLimit; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public boolean isReady() { return enabled && liveApiEnabled && !googleApiKey.isBlank() && dailyRequestLimit > 0; }
}
