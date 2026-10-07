package com.staminal.venue.overture;

import java.time.Instant;
import java.util.List;

public final class OvertureDraftMediaResponse {
    private OvertureDraftMediaResponse() { }
    public enum PhotoStatus { PENDING, APPROVED, REJECTED, ARCHIVED }
    public enum SourceKind { TEAM_PHOTO, BUSINESS_PROVIDED, LICENSED_IMAGE }
    public enum RightsBasis { TEAM_OWNED, BUSINESS_PERMISSION, OPEN_LICENSE }
    public record Actor(long adminId, String adminName) { }
    public record Limits(long maxInputBytes, long maxOutputBytes, int maxActivePhotos, long maxRetainedBytes, int maxLifetimePhotos) { }
    public record Photo(long id, long hallId, PhotoStatus status, String caption, SourceKind sourceKind,
            String sourceReference, RightsBasis rightsBasis, String licenseName, String permissionEvidence,
            boolean rightsConfirmed, Actor uploadedBy, Instant uploadedAt, int width, int height, int sizeBytes,
            String sha256, int sortOrder, Actor reviewedBy, Instant reviewedAt, String reviewReason,
            Actor archivedBy, Instant archivedAt, String archiveReason, boolean isCover,
            OverturePhotoCreditResponse.Credit credit) {
        public Photo(long id, long hallId, PhotoStatus status, String caption, SourceKind sourceKind,
                String sourceReference, RightsBasis rightsBasis, String licenseName, String permissionEvidence,
                boolean rightsConfirmed, Actor uploadedBy, Instant uploadedAt, int width, int height, int sizeBytes,
                String sha256, int sortOrder, Actor reviewedBy, Instant reviewedAt, String reviewReason,
                Actor archivedBy, Instant archivedAt, String archiveReason, boolean isCover) {
            this(id, hallId, status, caption, sourceKind, sourceReference, rightsBasis, licenseName, permissionEvidence,
                    rightsConfirmed, uploadedBy, uploadedAt, width, height, sizeBytes, sha256, sortOrder,
                    reviewedBy, reviewedAt, reviewReason, archivedBy, archivedAt, archiveReason, isCover, null);
        }
    }
    public record Gallery(long mediaVersion, Long coverMediaId, List<Photo> items, Limits limits,
            int activeCount, long retainedBytes) { }
}
