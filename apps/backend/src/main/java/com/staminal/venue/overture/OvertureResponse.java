package com.staminal.venue.overture;

import java.time.Instant;
import java.util.List;

public final class OvertureResponse {
    private OvertureResponse() { }

    public record Source(String dataset, String license, String recordId) { }

    public record CatalogVenue(String id, String name, String category, String address, String city,
            String area, String postcode, double latitude, double longitude, String phone, String website,
            Double confidence, String operatingStatus, List<Source> sources) { }

    public record Settings(boolean enabled, boolean ready, String catalogVersion, String release,
            String city, Instant generatedAt, int recordCount, int maxBatchSize) { }

    public record CatalogPage(String catalogVersion, String release, String city, Instant generatedAt,
            List<CatalogVenue> content, int page, int size, long totalElements, int totalPages) { }

    public enum Outcome { READY, CREATED, ALREADY_IMPORTED, POSSIBLE_DUPLICATE, INCOMPLETE }

    public record PreviewItem(CatalogVenue venue, Outcome outcome, Long hallId,
            List<Long> duplicateHallIds, List<String> issues) { }
    public record Preview(String catalogVersion, String release, List<PreviewItem> items) { }
    public record ImportItem(String sourceId, String name, Outcome outcome, Long hallId, List<String> issues) { }
    public record ImportResult(int createdCount, int skippedCount, List<ImportItem> items) { }

    public record Draft(long hallId, String sourceId, String name, String city, String area,
            String address, String category, String release, Instant importedAt, List<Source> sources,
            List<String> missingFields, String status, OvertureDraftReviewResponse.ReviewStatus reviewStatus, long reviewVersion) {
        public Draft(long hallId, String sourceId, String name, String city, String area, String address, String category,
                String release, Instant importedAt, List<Source> sources, List<String> missingFields, String status) {
            this(hallId, sourceId, name, city, area, address, category, release, importedAt, sources, missingFields, status,
                    OvertureDraftReviewResponse.ReviewStatus.UNREVIEWED, 0);
        }
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) { }
}
