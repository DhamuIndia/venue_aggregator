package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.staminal.venue.admin.AdminHallModerationService;
import com.staminal.venue.discovery.places.VenuePlacesClient;
import com.staminal.venue.halls.Service.HallsService;

/** Opt-in: migrates and writes only a fresh disposable local database with this prefix. */
@EnabledIfEnvironmentVariable(named = "OVERTURE_TEST_DB",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase3_test_[a-zA-Z0-9_]+")
@SpringBootTest(properties = {
        "app.overture-onboarding.enabled=true", "app.overture-onboarding.max-batch-size=20",
        "app.features.venue-discovery-enabled=false", "app.venue-discovery.live-api-enabled=false",
        "app.notifications.whatsapp.sending-enabled=false", "app.notifications.whatsapp.webhook-enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OvertureWorkflowIntegrationTest {
    private static final List<String> IDS = java.util.stream.IntStream.rangeClosed(1, 7)
            .mapToObj(i -> "69f6ec28-b302-4c7f-9bd2-c681ed50290" + i).toList();
    private static long existingHallId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("OVERTURE_TEST_DB");
        if (url == null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase3_test_[a-zA-Z0-9_]+")) {
            throw new IllegalStateException("A disposable local Phase 3 database is required");
        }
        // Seed an owner-managed hall BEFORE V41, proving the upgrade preserves existing data.
        Flyway.configure().dataSource(url, "venue_app", "venue_app_password")
                .locations("classpath:db/migration").target("40").load().migrate();
        try (var connection = DriverManager.getConnection(url, "venue_app", "venue_app_password");
                var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("select count(*) from halls")) {
                rows.next();
                if (rows.getLong(1) != 0) throw new IllegalStateException("The integration database must be fresh");
            }
            sql.executeUpdate("insert into users (full_name,phone,email,password_hash) values "
                    + "('Existing test owner','old-phase3-owner','old-phase3@example.invalid','synthetic')");
            try (var rows = sql.executeQuery("insert into halls (owner_user_id,owner_name,name,city,area,status,latitude,longitude) "
                    + "select id,'Existing test owner','Existing synthetic venue','Chennai','Existing area','APPROVED',13.25,80.35 "
                    + "from users where email='old-phase3@example.invalid' returning id")) {
                rows.next(); existingHallId = rows.getLong(1);
            }
            sql.executeUpdate("insert into hall_media (hall_id,media_type,url) values (" + existingHallId
                    + ",'IMAGE','https://example.invalid/existing-licensed-image.jpg')");
        }
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(OvertureFixtures.json());
        root.put("release", "2026-09-23.0");
        ArrayNode venues = root.putArray("venues");
        ObjectNode template = (ObjectNode) mapper.readTree(OvertureFixtures.json()).path("venues").get(0);
        for (int index = 0; index < IDS.size(); index++) {
            ObjectNode venue = template.deepCopy();
            venue.put("id", IDS.get(index));
            venue.put("name", index == 2 ? "Existing synthetic venue" : "Synthetic application venue " + index);
            venue.put("latitude", 12.85 + index * 0.02);
            venue.put("longitude", 80.1);
            venue.put("operatingStatus", "unknown");
            if (index == 2) venue.put("city", "Chennai");
            if (index == 3) venue.put("confidence", 0.5);
            if (index == 4) venue.put("operatingStatus", "permanently_closed");
            venues.add(venue);
        }
        Path catalog = Files.createTempFile("venuemart-overture-integration-", ".json");
        Files.writeString(catalog, mapper.writeValueAsString(root));
        catalog.toFile().deleteOnExit();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "venue_app");
        registry.add("spring.datasource.password", () -> "venue_app_password");
        registry.add("app.overture-onboarding.catalog-path", catalog::toString);
    }

    @Autowired private AdminOvertureOnboardingService service;
    @Autowired private AdminHallModerationService moderation;
    @Autowired private HallsService publicHalls;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private VenuePlacesClient places;

    @Test @Order(1) void migrationRepeatConcurrencyAndPrivateDraftsPreserveExistingBusinessData() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String email = "overture-" + tag + "@example.invalid";
        Long userId = jdbc.queryForObject("""
                insert into users (full_name,phone,email,password_hash)
                values ('Overture test admin',?,?, 'synthetic') returning id
                """, Long.class, "test" + tag, email);
        jdbc.update("insert into roles (name) values ('ADMIN') on conflict (name) do nothing");
        jdbc.update("insert into user_roles (user_id,role_id) select ?,id from roles where name='ADMIN'", userId);
        jdbc.update("""
                insert into admins (full_name,email,contact_number,password_hash)
                values ('Overture test admin',?,?, 'synthetic')
                """, email, "test" + tag);
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
        List<String> preserved = List.of("users", "vendors", "hall_media", "bookings", "enquiries", "lead_notification_jobs");
        var before = preserved.stream().collect(java.util.stream.Collectors.toMap(table -> table, this::count));
        var oldHall = jdbc.queryForMap("select * from halls where id=?", existingHallId);
        assertEquals("OWNER", oldHall.get("listing_origin"));
        assertEquals(41, jdbc.queryForObject("select max(version::integer) from flyway_schema_history where success", Integer.class));
        var settings = service.settings(auth);
        assertTrue(settings.ready());
        assertEquals("2026-09-23.0", settings.release());
        var preview = service.preview(new OvertureRequest.Preview(IDS.subList(0, 5)), auth);
        assertEquals(2, preview.items().stream().filter(item -> item.outcome() == OvertureResponse.Outcome.READY).count());
        assertEquals(1, preview.items().stream().filter(item -> item.outcome() == OvertureResponse.Outcome.POSSIBLE_DUPLICATE).count());
        assertEquals(2, preview.items().stream().filter(item -> item.outcome() == OvertureResponse.Outcome.INCOMPLETE).count());
        var request = new OvertureRequest.Import(settings.catalogVersion(), IDS.subList(0, 5));
        var created = service.importDrafts(request, auth);
        assertEquals(2, created.createdCount());
        assertEquals(3, created.skippedCount());
        assertEquals(0, service.importDrafts(request, auth).createdCount());
        assertEquals(2, service.importDrafts(request, auth).items().stream()
                .filter(item -> item.outcome() == OvertureResponse.Outcome.ALREADY_IMPORTED).count());
        var concurrent = new OvertureRequest.Import(settings.catalogVersion(), List.of(IDS.get(5)));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.<Callable<OvertureResponse.ImportResult>>of(
                    () -> service.importDrafts(concurrent, auth), () -> service.importDrafts(concurrent, auth)));
            assertEquals(1, futures.get(0).get().createdCount() + futures.get(1).get().createdCount());
        }
        assertEquals(3, count("venue_overture_imports"));
        assertEquals(4, count("halls"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.importDrafts(
                new OvertureRequest.Import("0".repeat(64), List.of(IDS.get(0))), auth)).getStatusCode());
        var drafts = service.drafts(0, 20, auth);
        assertEquals(3, drafts.totalElements());
        for (var draft : drafts.content()) {
            assertEquals("DRAFT", draft.status());
            assertEquals(jdbc.queryForObject("select imported_at from venue_overture_imports where hall_id=?",
                    java.sql.Timestamp.class, draft.hallId()).toInstant(), draft.importedAt());
            assertTrue(draft.missingFields().containsAll(List.of("operatingStatus", "city", "area", "photos", "capacity", "pricing")));
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                    () -> publicHalls.getPublicHall(String.valueOf(draft.hallId()))).getStatusCode());
        }
        assertEquals(1, moderation.getHalls(null, 0, 20).totalElements());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update halls set status='APPROVED' where id=?", drafts.content().getFirst().hallId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update halls set owner_user_id=null where id=?", existingHallId));
        assertEquals(oldHall, jdbc.queryForMap("select * from halls where id=?", existingHallId));
        for (var table : preserved) assertEquals(before.get(table), count(table), table + " must remain unchanged");
        assertEquals(3, jdbc.queryForObject("select count(*) from audit_events where action='OVERTURE_VENUE_DRAFT_CREATED'", Integer.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from halls where listing_origin='APPLICATION' "
                + "and owner_user_id is null and owner_name is null and status='DRAFT'", Integer.class));
        verifyNoInteractions(places);
    }

    @Test @Order(2) void auditFailureRollsBackHallAndSourceLinkTogether() {
        Long userId = jdbc.queryForObject("select id from users where email like 'overture-%@example.invalid'", Long.class);
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
        long hallsBefore = count("halls"), importsBefore = count("venue_overture_imports"), auditBefore = count("audit_events");
        jdbc.execute("""
                create function overture_test_reject_audit() returns trigger language plpgsql as $$
                begin
                    if NEW.action = 'OVERTURE_VENUE_DRAFT_CREATED' then
                        raise exception 'Synthetic integration audit failure';
                    end if;
                    return NEW;
                end $$
                """);
        jdbc.execute("create trigger overture_test_audit_failure before insert on audit_events "
                + "for each row execute function overture_test_reject_audit()");
        try {
            var request = new OvertureRequest.Import(service.settings(auth).catalogVersion(), List.of(IDS.get(6)));
            assertThrows(RuntimeException.class, () -> service.importDrafts(request, auth));
            assertEquals(hallsBefore, count("halls"));
            assertEquals(importsBefore, count("venue_overture_imports"));
            assertEquals(auditBefore, count("audit_events"));
            assertEquals(OvertureResponse.Outcome.READY,
                    service.preview(new OvertureRequest.Preview(List.of(IDS.get(6))), auth).items().getFirst().outcome());
        } finally {
            jdbc.execute("drop trigger overture_test_audit_failure on audit_events");
            jdbc.execute("drop function overture_test_reject_audit()");
        }
    }

    private long count(String table) {
        // All names are fixed test constants, never request data.
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
