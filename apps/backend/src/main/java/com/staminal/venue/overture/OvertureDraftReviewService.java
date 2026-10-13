package com.staminal.venue.overture;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.discovery.VenueDiscoveryAccess.Actor;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.overture.OvertureDraftReviewResponse.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OvertureDraftReviewService {
    private final JdbcTemplate jdbc;
    private final HallRepository halls;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final VenueDiscoveryAccess access;
    private final OvertureOnboardingProperties properties;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail detail(long hallId, Authentication authentication) {
        requireAccess(authentication);
        State state = state(hallId, false);
        Halls hall = hall(hallId);
        return detail(hall, state);
    }

    @Transactional
    public Detail update(long hallId, OvertureDraftReviewRequest.Update request, Authentication authentication) {
        Actor actor = requireAccess(authentication);
        if (request == null || request.expectedVersion() < 0) throw OvertureDraftReviewPolicy.bad("Invalid draft review request");
        // Share the import lock: new imported duplicate candidates cannot appear halfway through an assessment.
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, OvertureOnboardingStore.IMPORT_LOCK);
        State state = state(hallId, true);
        if (state.version() != request.expectedVersion())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This draft has changed; reload it before saving");
        Halls hall = hall(hallId);
        Facts before = Facts.from(hall, state.website(), state.operatingStatus());
        Facts after = OvertureDraftReviewPolicy.normalize(request.facts());
        String reviewNotes = OvertureDraftReviewPolicy.text(request.reviewNotes(), 4000, true);
        String duplicateNotes = OvertureDraftReviewPolicy.text(request.duplicateNotes(), 4000, true);
        boolean identityChanged = OvertureDraftReviewPolicy.identityChanged(before, after);
        List<Duplicate> duplicates = duplicates(hallId, after);
        DuplicateDecision decision = identityChanged ? DuplicateDecision.NOT_REVIEWED : request.duplicateDecision();
        List<Long> acknowledged = identityChanged ? List.of() : request.reviewedDuplicateHallIds();
        if (identityChanged) duplicateNotes = null;
        OvertureDraftReviewPolicy.requireDecision(request.reviewStatus(), decision, duplicateNotes, acknowledged, duplicates, identityChanged);
        Instant now = Instant.now();
        Map<String, Verification> verifications = OvertureDraftReviewPolicy.verifications(before, after, state.verifications(),
                request.verifiedFields(), reviewNotes, actor.admin().getId(), actor.admin().getFullName(), now);
        OvertureDraftReviewPolicy.requireVerified(after, verifications, request.reviewStatus());
        apply(hall, after);
        hall.setUpdatedAt(LocalDateTime.now());
        halls.saveAndFlush(hall);
        int changed = jdbc.update("""
                update venue_overture_draft_reviews set review_version=review_version+1, review_status=?,
                    verifications=?::jsonb, effective_website=?, effective_operating_status=?, duplicate_decision=?,
                    duplicate_notes=?, reviewed_duplicate_hall_ids=?::jsonb, review_notes=?,
                    last_reviewed_by=?, last_reviewer_name=?, last_reviewed_at=?
                where hall_id=? and review_version=?
                """, request.reviewStatus().name(), json(verifications), after.website(), after.operatingStatus(), decision.name(),
                duplicateNotes, json(acknowledged.stream().sorted().toList()), reviewNotes, actor.admin().getId(), actor.admin().getFullName(),
                java.sql.Timestamp.from(now), hallId, request.expectedVersion());
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "This draft has changed; reload it before saving");
        List<String> edited = after.fields().keySet().stream().filter(key -> !Objects.equals(before.fields().get(key), after.fields().get(key))).toList();
        audit.record(new AuditCommand(actor.userId(), actor.role(), AuditAction.OVERTURE_VENUE_DRAFT_REVIEWED,
                "HALL", String.valueOf(hallId), "Application venue draft reviewed", null,
                Map.of("reviewVersion", state.version() + 1, "reviewStatus", request.reviewStatus().name(), "duplicateDecision", decision.name()),
                Map.of("changedFields", edited, "verifiedFields", verifications.keySet().stream().sorted().toList())));
        return detail(hall, state(hallId, false));
    }

    private Actor requireAccess(Authentication authentication) {
        Actor actor = access.requireAdmin(authentication);
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Overture onboarding is disabled");
        return actor;
    }
    private Halls hall(long id) {
        return halls.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application venue draft not found"));
    }
    /** Internal read used by publication after its hall/review locks are acquired. */
    Detail publicationAssessment(long hallId) {
        Detail value = detail(hall(hallId), state(hallId, false, true), true);
        // Publication uses JDBC to change status; do not reuse a cached JPA status in this transaction.
        String status = jdbc.queryForObject("select status from halls where id=?", String.class, hallId);
        return new Detail(value.hallId(), value.sourceId(), status, value.reviewVersion(), value.reviewStatus(), value.facts(),
                value.sourceFacts(), value.sources(), value.release(), value.importedAt(), value.fieldOrigins(), value.verifications(),
                value.missingFields(), value.unverifiedFields(), value.duplicates(), value.duplicateDecision(), value.duplicateNotes(),
                value.reviewedDuplicateHallIds(), value.reviewNotes(), value.lastReviewedBy(), value.lastReviewedAt(), value.duplicateAssessmentLimited());
    }
    private State state(long hallId, boolean lock) { return state(hallId, lock, false); }
    private State state(long hallId, boolean lock, boolean includePublished) {
        if (hallId <= 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application venue draft not found");
        List<State> rows = jdbc.query("""
                select r.*, i.source_id::text, i.sources::text, i.release, i.imported_at
                from venue_overture_draft_reviews r join venue_overture_imports i on i.hall_id=r.hall_id
                join halls h on h.id=r.hall_id
                where r.hall_id=? and h.listing_origin='APPLICATION' and %s
                    and h.owner_user_id is null and h.owner_name is null
                """.formatted(includePublished ? "h.status in ('DRAFT','APPROVED')" : "h.status='DRAFT'") + (lock ? " for update of r, h" : ""), (row, index) -> read(row), hallId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application venue draft not found");
        return rows.getFirst();
    }
    private State read(ResultSet row) throws SQLException {
        Long reviewerId = row.getObject("last_reviewed_by", Long.class);
        return new State(row.getString("source_id"), row.getLong("review_version"), ReviewStatus.valueOf(row.getString("review_status")),
                readJson(row.getString("source_facts"), Facts.class),
                readJson(row.getString("verifications"), new TypeReference<Map<String, Verification>>() { }),
                row.getString("effective_website"), row.getString("effective_operating_status"),
                readJson(row.getString("sources"), new TypeReference<List<OvertureResponse.Source>>() { }),
                row.getString("release"), row.getTimestamp("imported_at").toInstant(),
                DuplicateDecision.valueOf(row.getString("duplicate_decision")), row.getString("duplicate_notes"),
                readJson(row.getString("reviewed_duplicate_hall_ids"), new TypeReference<List<Long>>() { }),
                row.getString("review_notes"), reviewerId == null ? null : new Reviewer(reviewerId, row.getString("last_reviewer_name")),
                row.getTimestamp("last_reviewed_at") == null ? null : row.getTimestamp("last_reviewed_at").toInstant());
    }
    private Detail detail(Halls hall, State state) {
        return detail(hall,state,false);
    }
    private Detail detail(Halls hall, State state, boolean tolerateDuplicateLimit) {
        Facts facts = Facts.from(hall, state.website(), state.operatingStatus());
        Map<String, Object> fields = facts.fields(), source = state.sourceFacts().fields();
        Map<String, String> origins = new LinkedHashMap<>();
        fields.forEach((key, value) -> origins.put(key, OvertureDraftReviewPolicy.missing(key, value) ? "MISSING"
                : Objects.equals(value, source.get(key)) ? "SOURCE" : "ADMIN"));
        List<String> missing = fields.entrySet().stream().filter(entry -> OvertureDraftReviewPolicy.missing(entry.getKey(), entry.getValue())).map(Map.Entry::getKey).toList();
        List<String> unverified = fields.entrySet().stream().filter(entry -> !OvertureDraftReviewPolicy.missing(entry.getKey(), entry.getValue())
                && !state.verifications().containsKey(entry.getKey())).map(Map.Entry::getKey).toList();
        List<Duplicate> duplicates;
        boolean assessmentLimited=false;
        try { duplicates=duplicates(hall.getId(),facts); }
        catch(DuplicateAssessmentLimitException exception) {
            if(!tolerateDuplicateLimit) throw exception;
            // Management/withdrawal must remain usable, but this is never a complete reviewed match set.
            duplicates=List.of(); assessmentLimited=true;
        }
        boolean staleDecision = assessmentLimited || state.decision() != DuplicateDecision.NOT_REVIEWED
                && !state.acknowledged().stream().sorted().toList().equals(duplicates.stream().map(Duplicate::hallId).sorted().toList());
        boolean unresolvedVerified = state.status() == ReviewStatus.VERIFIED && !duplicates.isEmpty()
                && (staleDecision || state.decision() != DuplicateDecision.DISTINCT);
        return new Detail(hall.getId(), state.sourceId(), hall.getStatus().name(), state.version(),
                assessmentLimited || unresolvedVerified || staleDecision && (state.status() == ReviewStatus.VERIFIED || state.status() == ReviewStatus.DUPLICATE)
                        ? ReviewStatus.IN_REVIEW : state.status(), facts, state.sourceFacts(),
                state.sources(), state.release(), state.importedAt(), Map.copyOf(origins), state.verifications(), missing, unverified, duplicates,
                staleDecision ? DuplicateDecision.NOT_REVIEWED : state.decision(), staleDecision ? null : state.duplicateNotes(),
                staleDecision ? List.of() : state.acknowledged(), state.reviewNotes(), state.reviewer(), state.reviewedAt(), assessmentLimited);
    }

    /** Indexed spatial bounding box plus normalized name/city, followed by exact 75 metre assessment. */
    private List<Duplicate> duplicates(long hallId, Facts facts) {
        String name = OvertureOnboardingStore.fold(facts.name()), city = OvertureOnboardingStore.fold(facts.city());
        Double latitude = facts.latitude(), longitude = facts.longitude();
        double latMargin = 0.0008;
        double lonMargin = latitude == null ? 0 : Math.min(180, 0.0008 / Math.max(0.000001, Math.cos(Math.toRadians(latitude))));
        List<Match> candidates = jdbc.query("""
                select id,name,city,area,status,listing_origin,latitude,longitude from halls
                where id<>? and (
                    (lower(btrim(regexp_replace(normalize(name,NFKC), '[[:space:]]+', ' ', 'g')))=?
                        and ?<>'' and lower(btrim(regexp_replace(normalize(city,NFKC), '[[:space:]]+', ' ', 'g')))=?)
                    or (?::double precision is not null and latitude between ? and ?
                        and (abs(longitude-?::double precision)<=? or abs(longitude-?::double precision)>=360-?)))
                order by id limit 201
                """, (row, index) -> new Match(row.getLong("id"), row.getString("name"), row.getString("city"), row.getString("area"),
                        row.getString("status"), row.getString("listing_origin"), row.getObject("latitude", Double.class), row.getObject("longitude", Double.class)),
                hallId, name, city, city, latitude, latitude == null ? null : latitude - latMargin,
                latitude == null ? null : latitude + latMargin, longitude, lonMargin, longitude, lonMargin);
        // Fail closed instead of silently binding an assessment to a partial match set.
        if (candidates.size() > 200) throw new DuplicateAssessmentLimitException("Too many possible matches; narrow the venue identity before reviewing");
        List<Duplicate> matches = candidates.stream().filter(candidate -> candidate.matches(facts)).map(candidate ->
                new Duplicate(candidate.id(), candidate.name(), candidate.city(), candidate.area(), candidate.status(), candidate.origin(), candidate.distance(facts))).toList();
        if (matches.size() > 100) throw new DuplicateAssessmentLimitException("Too many duplicate matches to review in one assessment");
        return matches;
    }
    private static final class DuplicateAssessmentLimitException extends ResponseStatusException {
        private DuplicateAssessmentLimitException(String reason) { super(HttpStatus.CONFLICT,reason); }
    }
    private record Match(long id, String name, String city, String area, String status, String origin, Double latitude, Double longitude) {
        boolean matches(Facts facts) {
            Double distance = distance(facts);
            return distance != null && distance <= 75 || facts.city() != null && city != null
                    && OvertureOnboardingStore.fold(facts.name()).equals(OvertureOnboardingStore.fold(name))
                    && OvertureOnboardingStore.fold(facts.city()).equals(OvertureOnboardingStore.fold(city));
        }
        Double distance(Facts facts) {
            if (latitude == null || longitude == null || facts.latitude() == null || facts.longitude() == null
                    || !Double.isFinite(latitude) || !Double.isFinite(longitude)) return null;
            double a = Math.pow(Math.sin(Math.toRadians(latitude - facts.latitude()) / 2), 2)
                    + Math.cos(Math.toRadians(facts.latitude())) * Math.cos(Math.toRadians(latitude))
                    * Math.pow(Math.sin(Math.toRadians(longitude - facts.longitude()) / 2), 2);
            return 6371000 * 2 * Math.atan2(Math.sqrt(Math.max(0, a)), Math.sqrt(Math.max(0, 1 - a)));
        }
    }
    private static void apply(Halls hall, Facts facts) {
        hall.setName(facts.name()); hall.setAddressLine(facts.address()); hall.setCity(facts.city()); hall.setArea(facts.area());
        hall.setPincode(facts.postcode()); hall.setLatitude(facts.latitude()); hall.setLongitude(facts.longitude());
        hall.setContactNumber(facts.phone()); hall.setCapacityMax(facts.capacity()); hall.setDescription(facts.description());
        Amenities amenities = facts.amenities();
        hall.setAcAvailable(amenities.ac()); hall.setCarParking(amenities.carParking()); hall.setBikeParking(amenities.bikeParking());
        hall.setDiningAvailable(amenities.dining()); hall.setGeneratorAvailable(amenities.generator()); hall.setLiftAvailable(amenities.lift());
        hall.setBridalRoomAvailable(amenities.bridalRoom()); hall.setCateringKitchenAvailable(amenities.cateringKitchen());
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("Could not save venue review"); }
    }
    private <T> T readJson(String value, Class<T> type) {
        try { return mapper.readValue(value, type); } catch (Exception exception) { throw new IllegalStateException("Stored venue review is invalid"); }
    }
    private <T> T readJson(String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); } catch (Exception exception) { throw new IllegalStateException("Stored venue review is invalid"); }
    }
    private record State(String sourceId, long version, ReviewStatus status, Facts sourceFacts, Map<String, Verification> verifications,
            String website, String operatingStatus, List<OvertureResponse.Source> sources, String release, Instant importedAt,
            DuplicateDecision decision, String duplicateNotes, List<Long> acknowledged, String reviewNotes, Reviewer reviewer, Instant reviewedAt) { }
}
