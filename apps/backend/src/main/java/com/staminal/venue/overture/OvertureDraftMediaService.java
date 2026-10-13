package com.staminal.venue.overture;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OvertureDraftMediaService {
    public static final int MAX_ACTIVE_PHOTOS = 20, MAX_LIFETIME_PHOTOS = 100;
    public static final long MAX_RETAINED_BYTES = 67108864;
    private static final Logger LOG = LoggerFactory.getLogger(OvertureDraftMediaService.class);
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final VenueDiscoveryAccess access;
    private final OvertureOnboardingProperties onboarding;
    private final OvertureDraftMediaProperties properties;
    private final OvertureDraftImageProcessor images;
    private final OvertureDraftMediaStorage storage;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Gallery gallery(long hallId, Authentication authentication) {
        actor(authentication); requireHall(hallId, false);
        return gallery(hallId);
    }

    @Transactional
    public Gallery upload(long hallId, MultipartFile file, OvertureDraftMediaRequest.Upload request, Authentication authentication) {
        var actor = actor(authentication);
        if (request == null) throw bad("Private photo metadata is required");
        State state = lockState(hallId, request.expectedVersion());
        var provenance = provenance(request);
        Gallery before = gallery(hallId);
        if (before.activeCount() >= MAX_ACTIVE_PHOTOS || before.items().size() >= MAX_LIFETIME_PHOTOS)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This draft has reached its photo count limit");
        if (before.retainedBytes() >= MAX_RETAINED_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "This draft has reached its retained photo storage limit");
        var image = images.process(file);
        byte[] bytes = image.bytes();
        if (!"image/jpeg".equals(image.contentType()) || bytes == null || bytes.length == 0 || bytes.length > OvertureDraftImageProcessor.MAX_OUTPUT_BYTES)
            throw new IllegalStateException("Canonical photo output is invalid");
        if (before.retainedBytes() + bytes.length > MAX_RETAINED_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "This photo exceeds the draft's retained storage limit");
        String key = "drafts/" + hallId + "/" + UUID.randomUUID() + ".jpg";
        String hash = hash(bytes);
        storage.ensurePrivate();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("A private photo upload requires a transaction");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int completionStatus) {
                if (completionStatus != STATUS_COMMITTED) cleanup(key);
            }
        });
        try { storage.put(key, bytes, "image/jpeg"); }
        catch (RuntimeException exception) { cleanup(key); throw exception; }
        Instant now = Instant.now();
        int sortOrder = before.items().stream().mapToInt(Photo::sortOrder).max().orElse(-1) + 1;
        Long id = jdbc.queryForObject("""
                insert into venue_overture_draft_photos
                    (hall_id,storage_key,size_bytes,width,height,sha256,caption,source_kind,source_reference,rights_basis,
                     license_name,permission_evidence,rights_confirmed,uploaded_by,uploader_name,uploaded_at,status,sort_order)
                values (?,?,?,?,?,?,?,?,?,?,?,?,true,?,?,?,'PENDING',?) returning id
                """, Long.class, hallId, key, bytes.length, image.width(), image.height(), hash, provenance.caption(),
                provenance.sourceKind().name(), provenance.sourceReference(), provenance.rightsBasis().name(), provenance.licenseName(),
                provenance.permissionEvidence(), actor.admin().getId(), actor.admin().getFullName(), java.sql.Timestamp.from(now), sortOrder);
        bump(hallId, state.version());
        record(actor, AuditAction.OVERTURE_DRAFT_PHOTO_UPLOADED, hallId, id,
                Map.of("mediaVersion", state.version() + 1, "status", "PENDING", "sourceKind", provenance.sourceKind().name(),
                        "rightsBasis", provenance.rightsBasis().name(), "sizeBytes", bytes.length, "sha256", hash));
        return gallery(hallId);
    }

    @Transactional
    public Gallery review(long hallId, long mediaId, OvertureDraftMediaRequest.Review request, Authentication authentication) {
        var actor = actor(authentication);
        if (request == null || request.status() != PhotoStatus.APPROVED && request.status() != PhotoStatus.REJECTED) throw bad("Photo review must approve or reject");
        State state = lockState(hallId, request.expectedVersion());
        Stored photo = photo(hallId, mediaId);
        if (photo.photo().status() != PhotoStatus.PENDING) throw conflict("Only pending photos may be reviewed; archive and upload a replacement for corrections");
        String reason = evidence(request.reason(), "Photo review reason");
        jdbc.update("""
                update venue_overture_draft_photos set status=?,reviewed_by=?,reviewer_name=?,reviewed_at=?,review_reason=?
                where id=? and hall_id=? and status='PENDING'
                """, request.status().name(), actor.admin().getId(), actor.admin().getFullName(), java.sql.Timestamp.from(Instant.now()), reason, mediaId, hallId);
        bump(hallId, state.version());
        record(actor, AuditAction.OVERTURE_DRAFT_PHOTO_REVIEWED, hallId, mediaId,
                Map.of("mediaVersion", state.version() + 1, "status", request.status().name()));
        return gallery(hallId);
    }

    @Transactional
    public Gallery arrange(long hallId, OvertureDraftMediaRequest.Arrangement request, Authentication authentication) {
        var actor = actor(authentication);
        if (request == null || request.orderedMediaIds() == null || request.orderedMediaIds().size() > MAX_ACTIVE_PHOTOS
                || request.orderedMediaIds().stream().anyMatch(id -> id == null || id <= 0)
                || request.orderedMediaIds().stream().distinct().count() != request.orderedMediaIds().size()) throw bad("Invalid photo arrangement");
        State state = lockState(hallId, request.expectedVersion());
        List<Long> approved = jdbc.query("select id from venue_overture_draft_photos where hall_id=? and status='APPROVED' order by id",
                (row, index) -> row.getLong(1), hallId);
        if (!approved.equals(request.orderedMediaIds().stream().sorted().toList())) throw conflict("Approved photos have changed; reload the latest gallery before arranging");
        if (request.coverMediaId() != null && !approved.contains(request.coverMediaId())) throw bad("The cover must be an approved photo in this draft");
        for (int index = 0; index < request.orderedMediaIds().size(); index++)
            jdbc.update("update venue_overture_draft_photos set sort_order=? where hall_id=? and id=? and status='APPROVED'",
                    index, hallId, request.orderedMediaIds().get(index));
        jdbc.update("update venue_overture_media_state set cover_media_id=? where hall_id=?", request.coverMediaId(), hallId);
        bump(hallId, state.version());
        record(actor, AuditAction.OVERTURE_DRAFT_PHOTO_ARRANGED, hallId, null,
                Map.of("mediaVersion", state.version() + 1, "photoIds", request.orderedMediaIds(),
                        "coverMediaId", request.coverMediaId() == null ? "NONE" : request.coverMediaId()));
        return gallery(hallId);
    }

    @Transactional
    public Gallery archive(long hallId, long mediaId, OvertureDraftMediaRequest.Archive request, Authentication authentication) {
        var actor = actor(authentication);
        if (request == null) throw bad("Archive reason is required");
        State state = lockState(hallId, request.expectedVersion());
        Stored photo = photo(hallId, mediaId);
        if (photo.photo().status() == PhotoStatus.ARCHIVED) throw conflict("This photo is already archived");
        String reason = evidence(request.reason(), "Archive reason");
        jdbc.update("""
                update venue_overture_draft_photos set status='ARCHIVED',archived_by=?,archiver_name=?,archived_at=?,archive_reason=?
                where id=? and hall_id=? and status<>'ARCHIVED'
                """, actor.admin().getId(), actor.admin().getFullName(), java.sql.Timestamp.from(Instant.now()), reason, mediaId, hallId);
        if (Long.valueOf(mediaId).equals(state.cover())) jdbc.update("update venue_overture_media_state set cover_media_id=null where hall_id=?", hallId);
        bump(hallId, state.version());
        record(actor, AuditAction.OVERTURE_DRAFT_PHOTO_ARCHIVED, hallId, mediaId,
                Map.of("mediaVersion", state.version() + 1, "status", "ARCHIVED"));
        // Archival retains canonical bytes and immutable provenance; content delivery now returns 404.
        return gallery(hallId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] content(long hallId, long mediaId, Authentication authentication) {
        actor(authentication); requireHall(hallId, false);
        Stored stored = photo(hallId, mediaId);
        if (stored.photo().status() == PhotoStatus.ARCHIVED) throw notFound();
        byte[] bytes = storage.read(stored.key(), stored.photo().sizeBytes());
        if (bytes == null || bytes.length != stored.photo().sizeBytes() || !hash(bytes).equals(stored.photo().sha256()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private photo content is unavailable");
        return bytes;
    }

    private VenueDiscoveryAccess.Actor actor(Authentication authentication) {
        var actor = access.requireAdmin(authentication);
        if (!onboarding.isEnabled() || !properties.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private draft photos are disabled");
        return actor;
    }
    private void requireHall(long hallId, boolean lock) {
        if (hallId <= 0) throw notFound();
        List<Long> rows = jdbc.query("""
                select h.id from halls h join venue_overture_imports i on i.hall_id=h.id
                join venue_overture_draft_reviews r on r.hall_id=h.id
                where h.id=? and h.listing_origin='APPLICATION' and h.status='DRAFT'
                    and h.owner_user_id is null and h.owner_name is null
                """ + (lock ? " for update of h" : ""), (row, index) -> row.getLong(1), hallId);
        if (rows.isEmpty()) throw notFound();
    }
    private State lockState(long hallId, long version) {
        if (version < 0) throw bad("Invalid media version");
        requireHall(hallId, true);
        jdbc.update("insert into venue_overture_media_state(hall_id) values (?) on conflict do nothing", hallId);
        State state = jdbc.queryForObject("select media_version,cover_media_id from venue_overture_media_state where hall_id=? for update",
                (row, index) -> new State(row.getLong("media_version"), row.getObject("cover_media_id", Long.class)), hallId);
        if (state == null || state.version() != version) throw conflict("This photo gallery has changed; reload the latest gallery before saving");
        return state;
    }
    private void bump(long hallId, long version) {
        if (jdbc.update("update venue_overture_media_state set media_version=media_version+1 where hall_id=? and media_version=?", hallId, version) != 1)
            throw conflict("This photo gallery has changed; reload the latest gallery before saving");
    }
    private Gallery gallery(long hallId) {
        List<State> states = jdbc.query("select media_version,cover_media_id from venue_overture_media_state where hall_id=?",
                (row, index) -> new State(row.getLong("media_version"), row.getObject("cover_media_id", Long.class)), hallId);
        State state = states.isEmpty() ? new State(0, null) : states.getFirst();
        List<Photo> photos = jdbc.query("""
                select p.*,c.credit_version,c.review_status as credit_status,c.title as credit_title,c.creator as credit_creator,
                    c.creator_url as credit_creator_url,c.source_url as credit_source_url,c.license_code as credit_license_code,
                    c.changes_notice as credit_changes_notice,c.required_notices as credit_required_notices,
                    c.changed_by as credit_changed_by,c.admin_name as credit_admin_name,c.changed_at as credit_changed_at,
                    c.reviewed_by as credit_reviewed_by,c.reviewer_name as credit_reviewer_name,c.reviewed_at as credit_reviewed_at,
                    c.review_reason as credit_review_reason
                from venue_overture_draft_photos p left join lateral (
                    select * from venue_overture_photo_credits credit where credit.hall_id=p.hall_id and credit.photo_id=p.id
                    order by credit_version desc limit 1
                ) c on true where p.hall_id=? order by p.sort_order,p.id limit 101
                """, (row, index) -> read(row, state.cover(), true).photo(), hallId);
        if (photos.size() > MAX_LIFETIME_PHOTOS) throw new IllegalStateException("Stored private photo count is invalid");
        return new Gallery(state.version(), state.cover(), List.copyOf(photos), new Limits(OvertureDraftImageProcessor.MAX_INPUT_BYTES,
                OvertureDraftImageProcessor.MAX_OUTPUT_BYTES, MAX_ACTIVE_PHOTOS, MAX_RETAINED_BYTES, MAX_LIFETIME_PHOTOS),
                (int) photos.stream().filter(photo -> photo.status() != PhotoStatus.ARCHIVED).count(), photos.stream().mapToLong(Photo::sizeBytes).sum());
    }
    private Stored photo(long hallId, long mediaId) {
        if (mediaId <= 0) throw notFound();
        List<Stored> rows = jdbc.query("select * from venue_overture_draft_photos where hall_id=? and id=?",
                (row, index) -> read(row, null, false), hallId, mediaId);
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }
    private Stored read(ResultSet row, Long cover, boolean includeCredit) throws SQLException {
        long id = row.getLong("id");
        return new Stored(new Photo(id, row.getLong("hall_id"), PhotoStatus.valueOf(row.getString("status")), row.getString("caption"),
                SourceKind.valueOf(row.getString("source_kind")), row.getString("source_reference"), RightsBasis.valueOf(row.getString("rights_basis")),
                row.getString("license_name"), row.getString("permission_evidence"), row.getBoolean("rights_confirmed"),
                new Actor(row.getLong("uploaded_by"), row.getString("uploader_name")), instant(row, "uploaded_at"), row.getInt("width"), row.getInt("height"),
                row.getInt("size_bytes"), row.getString("sha256"), row.getInt("sort_order"), actor(row, "reviewed_by", "reviewer_name"),
                instant(row, "reviewed_at"), row.getString("review_reason"), actor(row, "archived_by", "archiver_name"),
                instant(row, "archived_at"), row.getString("archive_reason"), Long.valueOf(id).equals(cover),
                includeCredit ? OverturePhotoCreditResponse.readGallery(row) : null), row.getString("storage_key"));
    }
    private static Actor actor(ResultSet row, String id, String name) throws SQLException {
        Long value = row.getObject(id, Long.class); return value == null ? null : new Actor(value, row.getString(name));
    }
    private static Instant instant(ResultSet row, String column) throws SQLException {
        var timestamp = row.getTimestamp(column); return timestamp == null ? null : timestamp.toInstant();
    }
    static OvertureDraftMediaRequest.Upload provenance(OvertureDraftMediaRequest.Upload request) {
        if (!request.rightsConfirmed() || request.sourceKind() == null || request.rightsBasis() == null) throw bad("Photo usage rights must be confirmed");
        RightsBasis expected = switch (request.sourceKind()) { case TEAM_PHOTO -> RightsBasis.TEAM_OWNED;
            case BUSINESS_PROVIDED -> RightsBasis.BUSINESS_PERMISSION; case LICENSED_IMAGE -> RightsBasis.OPEN_LICENSE; };
        if (request.rightsBasis() != expected) throw bad("Rights basis must match the photo source");
        String caption = OvertureDraftReviewPolicy.text(request.caption(), 500, true);
        String reference = OvertureDraftReviewPolicy.text(request.sourceReference(), 2048, false);
        if (reference != null) {
            String checked;
            try { checked = URLDecoder.decode(reference, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT); } catch (IllegalArgumentException exception) { throw bad("Invalid source reference"); }
            if (checked.contains("googleusercontent") || checked.contains("google maps") || checked.contains("maps.google.")
                    || checked.contains("maps.app.goo.gl") || checked.contains("goo.gl/maps") || checked.matches("(?s).*google\\.[^/\\s]+/maps.*"))
                throw bad("Google Maps and Google-hosted photo references cannot be ingested");
        }
        String license = OvertureDraftReviewPolicy.text(request.licenseName(), 180, false);
        if (request.sourceKind() == SourceKind.LICENSED_IMAGE && (license == null || reference == null)) throw bad("A licensed image requires a license name and source reference");
        if (request.sourceKind() != SourceKind.LICENSED_IMAGE && license != null) throw bad("Only licensed images may declare an open license");
        return new OvertureDraftMediaRequest.Upload(request.expectedVersion(), caption, request.sourceKind(), reference,
                request.rightsBasis(), license, evidence(request.permissionEvidence(), "Permission evidence"), true);
    }
    static String evidence(String value, String label) {
        String text = OvertureDraftReviewPolicy.text(value, 4000, true);
        if (text == null || text.length() < 10) throw bad(label + " must explain the evidence in 10 to 4000 characters");
        return text;
    }
    private void record(VenueDiscoveryAccess.Actor actor, AuditAction action, long hallId, Long photoId, Map<String, Object> details) {
        audit.record(new AuditCommand(actor.userId(), actor.role(), action, "OVERTURE_DRAFT_PHOTO",
                photoId == null ? "HALL:" + hallId : photoId.toString(), "Private application draft photo changed", null, details, Map.of("hallId", hallId)));
    }
    private void cleanup(String key) {
        try { storage.delete(key); } catch (RuntimeException exception) { LOG.warn("Private photo upload compensation needs operator attention for storage key {}", key); }
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }
    private static ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private static ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Private draft photo or gallery not found"); }
    private record State(long version, Long cover) { }
    private record Stored(Photo photo, String key) { }
}
