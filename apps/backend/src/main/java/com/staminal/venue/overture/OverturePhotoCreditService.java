package com.staminal.venue.overture;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.overture.OvertureDraftMediaResponse.Gallery;
import com.staminal.venue.overture.OverturePhotoCreditResponse.LicenseCode;
import com.staminal.venue.overture.OverturePhotoCreditResponse.Status;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OverturePhotoCreditService {
    public static final int MAX_REVISIONS = 100;
    private final JdbcTemplate jdbc;
    private final VenueDiscoveryAccess access;
    private final OvertureOnboardingProperties onboarding;
    private final OvertureDraftMediaProperties media;
    private final OvertureDraftMediaService galleries;
    private final AuditService audit;

    public Gallery save(long hallId, long photoId, OverturePhotoCreditRequest.Save request, Authentication authentication) {
        var actor = actor(authentication);
        var validated = OverturePhotoCreditPolicy.validate(request);
        lock(hallId, validated.expectedVersion());
        photo(hallId, photoId);
        long current = latest(hallId, photoId).map(CreditData::version).orElse(0L);
        nextVersion(current, validated.expectedCreditVersion());
        if (current >= MAX_REVISIONS - 1) throw conflict("The final credit revision slot is reserved for review; review the current pending revision or upload a replacement");
        insert(hallId, photoId, current + 1, Status.PENDING, validated, actor, null, null, false, false);
        bump(hallId, validated.expectedVersion());
        record(actor, AuditAction.OVERTURE_PHOTO_CREDIT_SAVED, hallId, photoId, current + 1, validated.expectedVersion() + 1, Status.PENDING);
        return galleries.gallery(hallId, authentication);
    }

    public Gallery review(long hallId, long photoId, OverturePhotoCreditRequest.Review request, Authentication authentication) {
        var actor = actor(authentication);
        if (request == null || request.expectedVersion() < 0 || request.expectedCreditVersion() < 0
                || request.status() != Status.APPROVED && request.status() != Status.REJECTED)
            throw bad("Photo credit review must approve or reject a current revision");
        String reason = OverturePhotoCreditPolicy.reason(request.reason());
        if (request.status() == Status.APPROVED && (!request.rightsConfirmed() || !request.attributionConfirmed()))
            throw bad("Approval requires confirmation of usage rights and public attribution");
        lock(hallId, request.expectedVersion());
        PhotoData photo = photo(hallId, photoId);
        CreditData current = latest(hallId, photoId).orElseThrow(() -> conflict("Save photo credits before reviewing them"));
        nextVersion(current.version(), request.expectedCreditVersion());
        if (current.status() != Status.PENDING) throw conflict("Only the latest pending credit revision may be reviewed; save a correction to restart review");
        var copied = OverturePhotoCreditPolicy.validate(new OverturePhotoCreditRequest.Save(request.expectedVersion(), current.version(),
                current.title(), current.creator(), current.creatorUrl(), current.sourceUrl(), current.licenseCode(), current.changesNotice(), current.requiredNotices()));
        if (request.status() == Status.APPROVED) {
            if (!"APPROVED".equals(photo.status())) throw conflict("Approve the photo's usage evidence before approving public credits");
            OverturePhotoCreditPolicy.requireLicenseMatch(photo.licenseName(), copied.licenseCode());
        }
        Instant reviewedAt = Instant.now();
        insert(hallId, photoId, current.version() + 1, request.status(), copied, actor, reviewedAt, reason, request.rightsConfirmed(), request.attributionConfirmed());
        bump(hallId, request.expectedVersion());
        record(actor, AuditAction.OVERTURE_PHOTO_CREDIT_REVIEWED, hallId, photoId, current.version() + 1, request.expectedVersion() + 1, request.status());
        return galleries.gallery(hallId, authentication);
    }

    private VenueDiscoveryAccess.Actor actor(Authentication authentication) {
        var actor = access.requireAdmin(authentication);
        if (!onboarding.isEnabled() || !media.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private draft photos are disabled");
        return actor;
    }
    private void lock(long hallId, long expectedVersion) {
        if (hallId <= 0 || expectedVersion < 0) throw bad("Invalid photo gallery or version");
        List<Long> rows = jdbc.query("""
                select h.id from halls h join venue_overture_imports i on i.hall_id=h.id
                join venue_overture_draft_reviews r on r.hall_id=h.id
                where h.id=? and h.listing_origin='APPLICATION' and h.status='DRAFT'
                    and h.owner_user_id is null and h.owner_name is null for update of h
                """, (row, index) -> row.getLong(1), hallId);
        if (rows.isEmpty()) throw notFound();
        jdbc.update("insert into venue_overture_media_state(hall_id) values (?) on conflict do nothing", hallId);
        Long version = jdbc.queryForObject("select media_version from venue_overture_media_state where hall_id=? for update", Long.class, hallId);
        if (version == null || version != expectedVersion) throw conflict("This photo gallery changed; reload before saving credits");
    }
    private PhotoData photo(long hallId, long photoId) {
        if (photoId <= 0) throw notFound();
        List<PhotoData> rows = jdbc.query("select source_kind,status,license_name from venue_overture_draft_photos where hall_id=? and id=?",
                (row, index) -> new PhotoData(row.getString("source_kind"), row.getString("status"), row.getString("license_name")), hallId, photoId);
        if (rows.isEmpty() || "ARCHIVED".equals(rows.getFirst().status())) throw notFound();
        if (!"LICENSED_IMAGE".equals(rows.getFirst().sourceKind())) throw conflict("Public license credits apply only to licensed photos");
        return rows.getFirst();
    }
    private java.util.Optional<CreditData> latest(long hallId, long photoId) {
        List<CreditData> rows = jdbc.query("""
                select credit_version,review_status,title,creator,creator_url,source_url,license_code,changes_notice,required_notices
                from venue_overture_photo_credits where hall_id=? and photo_id=? order by credit_version desc limit 1
                """, (row, index) -> new CreditData(row.getLong("credit_version"), Status.valueOf(row.getString("review_status")),
                        row.getString("title"), row.getString("creator"), row.getString("creator_url"), row.getString("source_url"),
                        LicenseCode.valueOf(row.getString("license_code")), row.getString("changes_notice"), row.getString("required_notices")), hallId, photoId);
        return rows.stream().findFirst();
    }
    private static void nextVersion(long current, long expected) {
        if (current != expected) throw conflict("Photo credits changed; reload the latest revision before saving");
        if (current >= MAX_REVISIONS) throw conflict("This photo reached its credit revision limit; archive and upload a replacement");
    }
    private void insert(long hallId, long photoId, long version, Status status, OverturePhotoCreditRequest.Save values,
            VenueDiscoveryAccess.Actor actor, Instant reviewedAt, String reason, boolean rights, boolean attribution) {
        jdbc.update("""
                insert into venue_overture_photo_credits(hall_id,photo_id,credit_version,title,creator,creator_url,source_url,
                    license_code,changes_notice,required_notices,review_status,changed_by,admin_name,changed_at,
                    reviewed_by,reviewer_name,reviewed_at,review_reason,rights_confirmed,attribution_confirmed)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, hallId, photoId, version, values.title(), values.creator(), values.creatorUrl(), values.sourceUrl(),
                values.licenseCode().name(), values.changesNotice(), values.requiredNotices(), status.name(), actor.admin().getId(),
                actor.admin().getFullName(), java.sql.Timestamp.from(Instant.now()), reviewedAt == null ? null : actor.admin().getId(),
                reviewedAt == null ? null : actor.admin().getFullName(), reviewedAt == null ? null : java.sql.Timestamp.from(reviewedAt), reason, rights, attribution);
    }
    private void bump(long hallId, long expected) {
        if (jdbc.update("update venue_overture_media_state set media_version=media_version+1 where hall_id=? and media_version=?", hallId, expected) != 1)
            throw conflict("This photo gallery changed; reload before saving credits");
    }
    private void record(VenueDiscoveryAccess.Actor actor, AuditAction action, long hallId, long photoId, long creditVersion, long mediaVersion, Status status) {
        audit.record(new AuditCommand(actor.userId(), actor.role(), action, "OVERTURE_PHOTO_CREDIT", String.valueOf(photoId),
                "Private photo credit revision recorded", null, Map.of("creditVersion", creditVersion, "mediaVersion", mediaVersion, "status", status.name()),
                Map.of("hallId", hallId)));
    }
    private record PhotoData(String sourceKind, String status, String licenseName) { }
    private record CreditData(long version, Status status, String title, String creator, String creatorUrl, String sourceUrl,
            LicenseCode licenseCode, String changesNotice, String requiredNotices) { }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Private licensed photo or draft not found"); }
}
