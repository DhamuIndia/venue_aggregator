package com.staminal.venue.overture;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import com.staminal.venue.overture.OvertureDraftMediaResponse.Actor;

public final class OverturePhotoCreditResponse {
    private OverturePhotoCreditResponse() { }
    public enum Status { PENDING, APPROVED, REJECTED }
    public enum LicenseCode { CC_BY_4_0, CC0_1_0 }
    public record PublicCredit(String title, String creator, String creatorUrl, String sourceUrl,
            String licenseCode, String licenseLabel, String licenseUrl, String changesNotice,
            String processingNotice, String requiredNotices) { }
    public record Credit(long creditVersion, Status status, String title, String creator, String creatorUrl,
            String sourceUrl, String licenseCode, String licenseLabel, String licenseUrl, String changesNotice,
            String processingNotice, String requiredNotices, Actor changedBy, Instant changedAt,
            Actor reviewedBy, Instant reviewedAt, String reviewReason) {
        public PublicCredit publicCredit() {
            return new PublicCredit(title, creator, creatorUrl, sourceUrl, licenseCode, licenseLabel, licenseUrl,
                    changesNotice, processingNotice, requiredNotices);
        }
    }
    static Credit readGallery(ResultSet row) throws SQLException {
        Long version = row.getObject("credit_version", Long.class);
        if (version == null) return null;
        String code = row.getString("credit_license_code");
        Long reviewer = row.getObject("credit_reviewed_by", Long.class);
        return new Credit(version, Status.valueOf(row.getString("credit_status")), row.getString("credit_title"),
                row.getString("credit_creator"), row.getString("credit_creator_url"), row.getString("credit_source_url"),
                code, OverturePhotoCreditPolicy.licenseLabel(code), OverturePhotoCreditPolicy.licenseUrl(code),
                row.getString("credit_changes_notice"), OverturePhotoCreditPolicy.PROCESSING_NOTICE,
                row.getString("credit_required_notices"), new Actor(row.getLong("credit_changed_by"), row.getString("credit_admin_name")),
                instant(row, "credit_changed_at"), reviewer == null ? null : new Actor(reviewer, row.getString("credit_reviewer_name")),
                instant(row, "credit_reviewed_at"), row.getString("credit_review_reason"));
    }
    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
