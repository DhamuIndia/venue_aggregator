package com.staminal.venue.overture;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class OverturePublicationResponse {
    private OverturePublicationResponse() { }
    public enum State { UNPUBLISHED, PUBLISHED }
    public record Summary(long hallId, String name, String city, String area, String hallStatus,
            State publicationState, long publicationVersion, long reviewVersion, long mediaVersion) { }
    public record History(long publicationVersion, State state, long adminId, String adminName, Instant at,
            String reason, long reviewVersion, long mediaVersion) { }
    public record Detail(long hallId, String name, String city, String area, String hallStatus,
            State publicationState, long publicationVersion, long reviewVersion, long mediaVersion,
            boolean ready, List<String> blockers, Long coverMediaId, List<Long> approvedPhotoIds,
            List<OvertureResponse.Source> sourceAttribution, String sourceRelease, List<History> history) { }
    /** Internal presentation data; no usage evidence, object identifiers or credentials. */
    public record PublicListing(long publicationVersion, List<String> galleryUrls,
            List<OvertureResponse.Source> sourceAttribution, String sourceRelease, Set<String> verifiedFields,
            List<PublicPhoto> applicationPhotos) {
        public PublicListing(long publicationVersion, List<String> galleryUrls,
                List<OvertureResponse.Source> sourceAttribution, String sourceRelease, Set<String> verifiedFields) {
            this(publicationVersion, galleryUrls, sourceAttribution, sourceRelease, verifiedFields, List.of());
        }
    }
    public record PublicPhoto(long photoId, String url, boolean requiresCredit,
            OverturePhotoCreditResponse.PublicCredit credit) { }
}
