package com.staminal.venue.overture;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.overture.OvertureCatalog.Snapshot;
import com.staminal.venue.overture.OvertureResponse.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminOvertureOnboardingService {
    private final VenueDiscoveryAccess access;
    private final OvertureOnboardingProperties properties;
    private final OvertureCatalog catalog;
    private final OvertureOnboardingStore store;

    public Settings settings(Authentication authentication) {
        access.requireAdmin(authentication);
        Snapshot snapshot = catalog.snapshot();
        return new Settings(properties.isEnabled(), properties.isEnabled() && snapshot != null,
                snapshot == null ? null : snapshot.version(), snapshot == null ? null : snapshot.release(),
                snapshot == null ? null : snapshot.city(), snapshot == null ? null : snapshot.generatedAt(),
                snapshot == null ? 0 : snapshot.venues().size(), properties.getMaxBatchSize());
    }

    public CatalogPage catalog(int page, int size, String query, Authentication authentication) {
        access.requireAdmin(authentication);
        Snapshot snapshot = requireCatalog();
        pagination(page, size);
        String filter = query == null ? "" : query.strip();
        if (filter.length() > 120 || filter.codePoints().anyMatch(Character::isISOControl)) throw bad("Invalid search query");
        String needle = filter.toLowerCase(Locale.ROOT);
        List<CatalogVenue> venues = snapshot.venues().stream().filter(venue ->
                (venue.name() + " " + text(venue.city()) + " " + text(venue.area()) + " " + venue.category())
                .toLowerCase(Locale.ROOT).contains(needle)).toList();
        int start = Math.min(page * size, venues.size()), end = Math.min(start + size, venues.size());
        return new CatalogPage(snapshot.version(), snapshot.release(), snapshot.city(), snapshot.generatedAt(),
                venues.subList(start, end), page, size, venues.size(), (venues.size() + size - 1) / size);
    }

    public Preview preview(OvertureRequest.Preview request, Authentication authentication) {
        access.requireAdmin(authentication);
        Snapshot snapshot = requireCatalog();
        List<CatalogVenue> venues = selected(snapshot, request == null ? null : request.ids());
        return new Preview(snapshot.version(), snapshot.release(), store.preview(venues));
    }

    public ImportResult importDrafts(OvertureRequest.Import request, Authentication authentication) {
        var actor = access.requireAdmin(authentication);
        Snapshot snapshot = requireCatalog();
        if (request == null || !snapshot.version().equals(request.catalogVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The catalog has changed; refresh and preview again");
        }
        return store.importDrafts(snapshot, selected(snapshot, request.ids()), actor);
    }

    public Page<Draft> drafts(int page, int size, Authentication authentication) {
        access.requireAdmin(authentication);
        requireEnabled();
        pagination(page, size);
        return store.drafts(page, size);
    }

    private List<CatalogVenue> selected(Snapshot snapshot, List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > properties.getMaxBatchSize()) throw bad("Invalid selection size");
        Set<String> unique = new HashSet<>();
        for (String id : ids) {
            if (!OvertureCatalog.validUuid(id) || !unique.add(id)) throw bad("Selection must contain unique venue IDs");
            if (!snapshot.byId().containsKey(id)) throw bad("A selected venue is not in this catalog");
        }
        return unique.stream().sorted().map(snapshot.byId()::get).toList();
    }

    private Snapshot requireCatalog() {
        requireEnabled();
        Snapshot snapshot = catalog.snapshot();
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Overture catalog is unavailable");
        return snapshot;
    }
    private void requireEnabled() {
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Overture onboarding is disabled");
    }
    private static void pagination(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 50) throw bad("Invalid pagination");
    }
    private static String text(String value) { return value == null ? "" : value; }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
