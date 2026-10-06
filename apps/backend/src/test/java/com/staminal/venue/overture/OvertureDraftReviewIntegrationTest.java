package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.staminal.venue.admin.AdminHallModerationService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.discovery.places.VenuePlacesClient;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.overture.OvertureDraftReviewRequest.Update;
import com.staminal.venue.overture.OvertureDraftReviewResponse.Detail;

/** Writes only an explicitly named, fresh disposable local database. Never production. */
@EnabledIfEnvironmentVariable(named = "OVERTURE_REVIEW_TEST_DB",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase4a_test_[a-zA-Z0-9_]+")
@SpringBootTest(properties = {
        "app.overture-onboarding.enabled=true", "app.overture-onboarding.catalog-path=",
        "app.features.venue-discovery-enabled=false", "app.venue-discovery.live-api-enabled=false",
        "app.notifications.whatsapp.sending-enabled=false", "app.notifications.whatsapp.webhook-enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OvertureDraftReviewIntegrationTest {
    private static long adminUserId, ownerHallId, draftHallId;
    private static String originalSourceJson;
    private static Map<String, Object> originalOwner;
    private static Map<String, Long> businessCounts;
    private static final List<String> REQUIRED = List.of("name", "address", "city", "area", "phone",
            "latitude", "longitude", "operatingStatus", "capacity");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("OVERTURE_REVIEW_TEST_DB");
        if (url == null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase4a_test_[a-zA-Z0-9_]+"))
            throw new IllegalStateException("A disposable local Slice 4A database is required");
        // Seed existing Phase 3 records before V42 to exercise the real backfill.
        Flyway.configure().dataSource(url, "venue_app", "venue_app_password")
                .locations("classpath:db/migration").target("41").load().migrate();
        try (var connection = DriverManager.getConnection(url, "venue_app", "venue_app_password");
                var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("select count(*) from halls")) {
                rows.next();
                if (rows.getLong(1) != 0) throw new IllegalStateException("Integration database must be fresh");
            }
            sql.executeUpdate("insert into users (full_name,phone,email,password_hash) values "
                    + "('Slice 4A admin','synthetic-4a-admin','admin4a@example.invalid','synthetic'),"
                    + "('Existing owner','synthetic-4a-owner','owner4a@example.invalid','synthetic')");
            sql.executeUpdate("insert into roles(name) values('ADMIN') on conflict(name) do nothing");
            sql.executeUpdate("insert into user_roles(user_id,role_id) select u.id,r.id from users u,roles r "
                    + "where u.email='admin4a@example.invalid' and r.name='ADMIN'");
            sql.executeUpdate("insert into admins(full_name,email,contact_number,password_hash) "
                    + "values('Slice 4A admin','admin4a@example.invalid','synthetic-4a-admin','synthetic')");
            try (var rows = sql.executeQuery("select id from users where email='admin4a@example.invalid'")) {
                rows.next(); adminUserId = rows.getLong(1);
            }
            try (var rows = sql.executeQuery("insert into halls(owner_user_id,owner_name,name,city,area,status,latitude,longitude) "
                    + "select id,'Existing owner','Existing owner venue','Chennai','T Nagar','APPROVED',13.15,80.3 "
                    + "from users where email='owner4a@example.invalid' returning id")) {
                rows.next(); ownerHallId = rows.getLong(1);
            }
            sql.executeUpdate("insert into hall_media(hall_id,media_type,url) values(" + ownerHallId
                    + ",'IMAGE','https://example.invalid/existing-photo.jpg')");
            try (var rows = sql.executeQuery("insert into halls(listing_origin,status,name,address_line,city,latitude,longitude,hall_type) "
                    + "values('APPLICATION','DRAFT','Imported original venue','12 Source Street','Chennai',13.02,80.2,'event_venue') returning id")) {
                rows.next(); draftHallId = rows.getLong(1);
            }
            sql.executeUpdate("insert into venue_overture_imports(source_id,hall_id,catalog_version,release,category,"
                    + "source_website,source_operating_status,sources,created_by_admin) select "
                    + "'69f6ec28-b302-4c7f-9bd2-c681ed502910'," + draftHallId + ",'" + "0".repeat(64) + "',"
                    + "'2026-09-23.1','event_venue','http://original.example.org','unknown',"
                    + "'[{\"dataset\":\"microsoft\",\"license\":\"CDLA-Permissive-2.0\",\"recordId\":\"synthetic-origin\"}]'::jsonb,id "
                    + "from admins where email='admin4a@example.invalid'");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "venue_app");
        registry.add("spring.datasource.password", () -> "venue_app_password");
    }

    @Autowired private OvertureDraftReviewService review;
    @Autowired private AdminOvertureOnboardingService onboarding;
    @Autowired private OvertureOnboardingStore importStore;
    @Autowired private VenueDiscoveryAccess access;
    @Autowired private AdminHallModerationService moderation;
    @Autowired private HallsService publicHalls;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private VenuePlacesClient places;

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(Long.toString(adminUserId), null, List.of());
    }

    @Test @Order(1) void migrationBackfillsSourceFactsAndAllowsReviewWithoutCatalogFile() {
        assertEquals(43, jdbc.queryForObject("select max(version::integer) from flyway_schema_history where success", Integer.class));
        assertFalse(onboarding.settings(auth()).ready());
        var detail = review.detail(draftHallId, auth());
        assertEquals(0, detail.reviewVersion());
        assertEquals("UNREVIEWED", detail.reviewStatus().name());
        assertEquals(detail.facts(), detail.sourceFacts());
        assertNull(detail.sourceFacts().area());
        assertNull(detail.sourceFacts().amenities().carParking());
        assertEquals("http://original.example.org", detail.sourceFacts().website());
        assertEquals("unknown", detail.sourceFacts().operatingStatus());
        assertEquals("SOURCE", detail.fieldOrigins().get("name"));
        assertEquals("MISSING", detail.fieldOrigins().get("area"));
        assertTrue(detail.verifications().isEmpty());
        originalSourceJson = sourceJson();
        originalOwner = jdbc.queryForMap("select * from halls where id=?", ownerHallId);
        businessCounts = List.of("users", "vendors", "hall_media", "bookings", "enquiries", "lead_notification_jobs")
                .stream().collect(java.util.stream.Collectors.toMap(table -> table, this::count));
    }

    @Test @Order(2) void editsVerificationInvalidationAndConcurrentUpdatesPreserveSource() throws Exception {
        var original = review.detail(draftHallId, auth());
        var first = review.update(draftHallId, request(original, facts -> {
            facts.put("area", "Adyar"); facts.put("capacity", 200);
            ((ObjectNode) facts.get("amenities")).put("carParking", false);
        }, List.of("area", "capacity", "amenities.carParking"), "IN_REVIEW", "Checked synthetic venue facts"), auth());
        assertEquals(1, first.reviewVersion());
        assertFalse(first.facts().amenities().carParking());
        assertEquals("ADMIN", first.fieldOrigins().get("capacity"));
        assertEquals("Slice 4A admin", first.verifications().get("capacity").adminName());
        assertNotNull(first.verifications().get("amenities.carParking").verifiedAt());
        assertNull(first.sourceFacts().capacity());
        var second = review.update(draftHallId, request(first, facts -> facts.put("capacity", 250),
                List.of("area", "amenities.carParking"), "IN_REVIEW", "Capacity changed, pending verification"), auth());
        assertFalse(second.verifications().containsKey("capacity"));
        assertEquals(first.verifications().get("area"), second.verifications().get("area"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> review.update(draftHallId,
                request(first, facts -> {}, List.of(), "IN_REVIEW", null), auth())).getStatusCode());
        var left = request(second, facts -> facts.put("description", "Concurrent A"), List.copyOf(second.verifications().keySet()), "IN_REVIEW", null);
        var right = request(second, facts -> facts.put("description", "Concurrent B"), List.copyOf(second.verifications().keySet()), "IN_REVIEW", null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.<Callable<Boolean>>of(() -> attempt(left), () -> attempt(right)));
            assertNotEquals(futures.get(0).get(), futures.get(1).get(), "Exactly one optimistic update may commit");
        }
        assertEquals(3, review.detail(draftHallId, auth()).reviewVersion());
        assertEquals(originalSourceJson, sourceJson());
    }

    @Test @Order(3) void factualVerificationAndDuplicateDecisionsRemainPrivate() throws Exception {
        var current = review.detail(draftHallId, auth());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> review.update(draftHallId,
                request(current, facts -> {}, List.copyOf(current.verifications().keySet()), "VERIFIED", "Incomplete facts"), auth())).getStatusCode());
        var verified = review.update(draftHallId, request(current, facts -> {
            facts.put("phone", "+919876123450"); facts.put("operatingStatus", "open");
        }, REQUIRED, "VERIFIED", "Confirmed minimum venue facts in synthetic test"), auth());
        assertEquals("VERIFIED", verified.reviewStatus().name());
        assertEquals("DRAFT", verified.status());
        assertEquals("unknown", verified.sourceFacts().operatingStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> publicHalls.getPublicHall(Long.toString(draftHallId))).getStatusCode());
        // A new match must invalidate the read projection even if the original empty set was NOT_REVIEWED.
        jdbc.update("update halls set latitude=?,longitude=? where id=?",
                verified.facts().latitude(), verified.facts().longitude(), ownerHallId);
        try {
            var refreshed = review.detail(draftHallId, auth());
            assertEquals("IN_REVIEW", refreshed.reviewStatus().name());
            assertEquals("NOT_REVIEWED", refreshed.duplicateDecision().name());
            assertEquals(List.of(ownerHallId), refreshed.duplicates().stream().map(item -> item.hallId()).toList());
            assertEquals(verified.reviewVersion(), refreshed.reviewVersion());
            assertEquals(verified.verifications(), refreshed.verifications());
            assertEquals("VERIFIED", jdbc.queryForObject(
                    "select review_status from venue_overture_draft_reviews where hall_id=?", String.class, draftHallId));
        } finally {
            jdbc.update("update halls set latitude=?,longitude=? where id=?",
                    originalOwner.get("latitude"), originalOwner.get("longitude"), ownerHallId);
        }
        var moved = review.update(draftHallId, request(verified, facts -> {
            facts.put("latitude", 13.15); facts.put("longitude", 80.3);
        }, List.of("name", "address", "city", "area", "phone", "operatingStatus", "capacity"),
                "IN_REVIEW", "Location correction requires a fresh duplicate review"), auth());
        assertEquals(List.of(ownerHallId), moved.duplicates().stream().map(item -> item.hallId()).toList());
        assertEquals("NOT_REVIEWED", moved.duplicateDecision().name());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> review.update(draftHallId,
                request(moved, facts -> {}, REQUIRED, "VERIFIED", "Facts checked but duplicate unresolved"), auth())).getStatusCode());
        var distinctRequest = request(moved, facts -> {}, REQUIRED, "VERIFIED", "Coordinates independently checked");
        var distinct = new Update(distinctRequest.expectedVersion(), distinctRequest.facts(), distinctRequest.verifiedFields(),
                distinctRequest.reviewStatus(), distinctRequest.reviewNotes(),
                OvertureDraftReviewResponse.DuplicateDecision.DISTINCT, "Separate venue in same complex", List.of(ownerHallId));
        var reviewed = review.update(draftHallId, distinct, auth());
        assertEquals("VERIFIED", reviewed.reviewStatus().name());
        var duplicateRequest = request(reviewed, facts -> {}, REQUIRED, "DUPLICATE", "Reassessment in synthetic test");
        var duplicate = new Update(duplicateRequest.expectedVersion(), duplicateRequest.facts(), duplicateRequest.verifiedFields(),
                duplicateRequest.reviewStatus(), duplicateRequest.reviewNotes(),
                OvertureDraftReviewResponse.DuplicateDecision.CONFIRMED_DUPLICATE, "Confirmed matching record", List.of(ownerHallId));
        var flagged = review.update(draftHallId, duplicate, auth());
        assertEquals("DUPLICATE", flagged.reviewStatus().name());
        assertEquals("DRAFT", flagged.status());
        assertEquals(originalOwner, jdbc.queryForMap("select * from halls where id=?", ownerHallId));
        assertEquals(originalSourceJson, sourceJson());
    }

    @Test @Order(4) void auditFailureRollsBackEditsAndImmutableSnapshotCannotChange() throws Exception {
        var before = review.detail(draftHallId, auth());
        long auditsBefore = count("audit_events");
        jdbc.execute("""
                create function slice4a_reject_audit() returns trigger language plpgsql as $$
                begin
                    if NEW.action = 'OVERTURE_VENUE_DRAFT_REVIEWED' then raise exception 'Synthetic audit failure'; end if;
                    return NEW;
                end $$
                """);
        jdbc.execute("create trigger slice4a_audit_failure before insert on audit_events for each row execute function slice4a_reject_audit()");
        try {
            assertThrows(RuntimeException.class, () -> review.update(draftHallId, request(before,
                    facts -> facts.put("description", "Must roll back"), List.copyOf(before.verifications().keySet()),
                    "DUPLICATE", "Synthetic atomicity check"), auth()));
            assertEquals(before, review.detail(draftHallId, auth()));
            assertEquals(auditsBefore, count("audit_events"));
        } finally {
            jdbc.execute("drop trigger slice4a_audit_failure on audit_events");
            jdbc.execute("drop function slice4a_reject_audit()");
        }
        assertThrows(RuntimeException.class, () -> jdbc.update("update venue_overture_draft_reviews set source_facts='{}'::jsonb where hall_id=?", draftHallId));
        assertEquals(originalSourceJson, sourceJson());
    }

    @Test @Order(5) void newImportsInitializeReviewStateAndAllBusinessIsolationGuardsRemain() throws Exception {
        var imported = importStore.importDrafts(OvertureFixtures.snapshot(), OvertureFixtures.snapshot().venues(), access.requireAdmin(auth()));
        assertEquals(1, imported.createdCount());
        var newDraft = review.detail(imported.items().getFirst().hallId(), auth());
        assertEquals("UNREVIEWED", newDraft.reviewStatus().name());
        assertEquals(newDraft.facts(), newDraft.sourceFacts());
        assertEquals(0, newDraft.reviewVersion());
        assertEquals(1, moderation.getHalls(null, 0, 20).totalElements());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> review.detail(ownerHallId, auth())).getStatusCode());
        var ownerRequest = request(review.detail(draftHallId, auth()), facts -> {}, REQUIRED, "DUPLICATE", "Must not affect owner");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> review.update(ownerHallId, ownerRequest, auth())).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> review.detail(draftHallId, null)).getStatusCode());
        jdbc.update("update users set status='INACTIVE' where id=?", adminUserId);
        try {
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> review.update(draftHallId, ownerRequest, auth())).getStatusCode());
        } finally { jdbc.update("update users set status='ACTIVE' where id=?", adminUserId); }
        assertThrows(RuntimeException.class, () -> jdbc.update("update halls set status='APPROVED' where id=?", draftHallId));
        assertEquals(originalOwner, jdbc.queryForMap("select * from halls where id=?", ownerHallId));
        for (var table : businessCounts.keySet()) assertEquals(businessCounts.get(table), count(table), table + " must remain unchanged");
        assertEquals("http://original.example.org", jdbc.queryForObject("select source_website from venue_overture_imports where hall_id=?", String.class, draftHallId));
        assertEquals("unknown", jdbc.queryForObject("select source_operating_status from venue_overture_imports where hall_id=?", String.class, draftHallId));
        verifyNoInteractions(places);
    }

    private Update request(Detail detail, Consumer<ObjectNode> change, List<String> verified, String state, String notes) throws Exception {
        ObjectNode facts = mapper.valueToTree(detail.facts()); change.accept(facts);
        ObjectNode body = mapper.createObjectNode();
        body.put("expectedVersion", detail.reviewVersion()); body.set("facts", facts);
        body.set("verifiedFields", mapper.valueToTree(verified)); body.put("reviewStatus", state);
        body.put("reviewNotes", notes); body.put("duplicateDecision", detail.duplicateDecision().name());
        body.put("duplicateNotes", detail.duplicateNotes()); body.set("reviewedDuplicateHallIds", mapper.valueToTree(detail.reviewedDuplicateHallIds()));
        return mapper.treeToValue(body, Update.class);
    }
    private boolean attempt(Update update) {
        try { review.update(draftHallId, update, auth()); return true; }
        catch (ResponseStatusException exception) { assertEquals(HttpStatus.CONFLICT, exception.getStatusCode()); return false; }
    }
    private String sourceJson() {
        return jdbc.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?", String.class, draftHallId);
    }
    private long count(String table) {
        // All table names are fixed test constants, never request input.
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
