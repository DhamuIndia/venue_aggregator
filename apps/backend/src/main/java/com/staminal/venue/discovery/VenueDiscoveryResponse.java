package com.staminal.venue.discovery;

import java.time.Instant;
import java.util.List;
import com.staminal.venue.discovery.places.PlacePreview;

public final class VenueDiscoveryResponse {
    private VenueDiscoveryResponse() { }

    public record VenueType(String value, String label) { }
    public record Settings(boolean enabled, boolean liveApiEnabled, boolean ready,
            int maxResults, int dailyRequestLimit, int requestsUsedToday, List<VenueType> venueTypes) { }
    public record Candidate(Long id, String placeId, String status, Instant discoveredAt,
            Instant sourceCheckedAt, Instant statusChangedAt, Long linkedHallId) { }
    public record Run(Long id, String city, String area, String venueType, String status,
            Instant createdAt, Instant startedAt, Instant completedAt, String failureReason, long resultCount) { }
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
    public record RunDetail(Run run, List<Candidate> candidates) { }
    public record SearchResult(Run run, List<Candidate> candidates, List<PlacePreview> previews) { }
}
