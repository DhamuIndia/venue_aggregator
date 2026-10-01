package com.staminal.venue.discovery;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.discovery.VenueDiscoveryResponse.*;
import com.staminal.venue.discovery.places.PlacePreview;
import com.staminal.venue.discovery.places.VenuePlacesClient;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminVenueDiscoveryService {
    private final VenueDiscoveryAccess access;
    private final VenueDiscoveryProperties properties;
    private final VenueDiscoveryQuota quota;
    private final VenueDiscoveryStore store;
    private final VenuePlacesClient places;

    public Settings settings(Authentication authentication) {
        access.requireAdmin(authentication);
        return new Settings(properties.isEnabled(), properties.isLiveApiEnabled(), properties.isReady(),
                properties.getMaxResults(), properties.getDailyRequestLimit(), quota.usedToday(),
                Arrays.stream(VenueDiscoveryVenueType.values()).map(t -> new VenueType(t.name(), t.getLabel())).toList());
    }

    public SearchResult search(VenueDiscoveryRequest.Search request, Authentication authentication) {
        var actor = access.requireAdmin(authentication);
        requireLive();
        quota.reserve();
        Long runId = store.start(request, actor);
        String query = request.venueType().getLabel() + " in "
                + (request.area() == null || request.area().isBlank() ? "" : request.area().trim() + ", ")
                + request.city().trim() + ", India";
        try {
            List<PlacePreview> previews = places.search(query, properties.getMaxResults());
            var result = store.complete(runId, previews.stream().map(PlacePreview::placeId).toList(), actor);
            return new SearchResult(result.run(), result.candidates(), previews);
        } catch (RuntimeException exception) {
            store.fail(runId);
            if (exception instanceof ResponseStatusException safe) {
                throw safe;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Venue search failed; please try again later");
        }
    }

    public Page<Run> runs(int page, int size, Authentication authentication) {
        access.requireAdmin(authentication);
        requireEnabled();
        validatePage(page, size);
        return store.runs(page, size);
    }

    public RunDetail run(Long id, Authentication authentication) {
        access.requireAdmin(authentication);
        requireEnabled();
        return store.run(id);
    }

    public Page<Candidate> candidates(VenueDiscoveryCandidateStatus status, int page, int size,
            Authentication authentication) {
        access.requireAdmin(authentication);
        requireEnabled();
        validatePage(page, size);
        return store.candidates(status, page, size);
    }

    public PlacePreview preview(Long id, Authentication authentication) {
        access.requireAdmin(authentication);
        requireLive();
        String placeId = store.placeId(id);
        quota.reserve();
        PlacePreview preview = places.details(placeId);
        store.checked(id);
        return preview;
    }

    public Candidate review(Long id, VenueDiscoveryRequest.Review request, Authentication authentication) {
        var actor = access.requireAdmin(authentication);
        requireEnabled();
        return store.review(id, request, actor);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Venue discovery is disabled");
        }
    }

    private void requireLive() {
        requireEnabled();
        if (!properties.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Live venue discovery is not configured or enabled");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0–100000 and size 1–50");
        }
    }
}
