package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.discovery.places.VenuePlacesClient;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;

/** Requires a fresh local database and a separately provisioned disposable PRIVATE MinIO bucket. */
@EnabledIfEnvironmentVariable(named = "OVERTURE_MEDIA_TEST_DB",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase4b_test_[a-zA-Z0-9_]+")
@SpringBootTest(properties = {
        "app.overture-onboarding.enabled=true", "app.overture-onboarding.media.enabled=true",
        "app.overture-onboarding.catalog-path=", "app.storage.s3.endpoint=http://localhost:9000",
        "app.storage.s3.presign-endpoint=http://localhost:9000", "app.storage.s3.bucket=venue-media",
        "app.storage.s3.access-key=venue_minio", "app.storage.s3.secret-key=venue_minio_password",
        "app.storage.s3.region=us-east-1", "app.storage.s3.path-style-access=true",
        "app.features.venue-discovery-enabled=false", "app.venue-discovery.live-api-enabled=false",
        "app.notifications.whatsapp.sending-enabled=false", "app.notifications.whatsapp.webhook-enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OvertureDraftMediaIntegrationTest {
    private static long adminUserId, ownerHallId, draftHallId;
    private static long firstPhotoId;
    private static String firstStorageKey, originalSourceJson;
    private static Map<String, Object> originalOwner, originalDraft;
    private static Map<String, Long> businessCounts;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("OVERTURE_MEDIA_TEST_DB"), bucket = System.getenv("OVERTURE_MEDIA_TEST_BUCKET");
        if (url == null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase4b_test_[a-zA-Z0-9_]+")
                || bucket == null || !bucket.matches("venuemart-phase4b-test-[a-z0-9-]+"))
            throw new IllegalStateException("Fresh local Slice 4B database and private test bucket are required");
        Flyway.configure().dataSource(url, "venue_app", "venue_app_password")
                .locations("classpath:db/migration").target("41").load().migrate();
        try (var connection = DriverManager.getConnection(url, "venue_app", "venue_app_password");
                var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("select count(*) from halls")) {
                rows.next(); if (rows.getLong(1) != 0) throw new IllegalStateException("Test database must be fresh");
            }
            sql.executeUpdate("insert into users(full_name,phone,email,password_hash) values "
                    + "('Media test admin','synthetic-4b-admin','admin4b@example.invalid','synthetic'),"
                    + "('Existing owner','synthetic-4b-owner','owner4b@example.invalid','synthetic')");
            sql.executeUpdate("insert into roles(name) values('ADMIN') on conflict(name) do nothing");
            sql.executeUpdate("insert into user_roles(user_id,role_id) select u.id,r.id from users u,roles r "
                    + "where u.email='admin4b@example.invalid' and r.name='ADMIN'");
            sql.executeUpdate("insert into admins(full_name,email,contact_number,password_hash) "
                    + "values('Media test admin','admin4b@example.invalid','synthetic-4b-admin','synthetic')");
            try (var rows = sql.executeQuery("select id from users where email='admin4b@example.invalid'")) {
                rows.next(); adminUserId = rows.getLong(1);
            }
            try (var rows = sql.executeQuery("insert into halls(owner_user_id,owner_name,name,city,area,status,cover_image_url) "
                    + "select id,'Existing owner','Existing owner hall','Chennai','Adyar','APPROVED',"
                    + "'https://example.invalid/existing-cover.jpg' from users where email='owner4b@example.invalid' returning id")) {
                rows.next(); ownerHallId = rows.getLong(1);
            }
            sql.executeUpdate("insert into hall_media(hall_id,media_type,url) values(" + ownerHallId
                    + ",'IMAGE','https://example.invalid/existing-photo.jpg')");
            try (var rows = sql.executeQuery("insert into halls(listing_origin,status,name,address_line,city,hall_type) "
                    + "values('APPLICATION','DRAFT','Synthetic media venue','12 Test Street','Chennai','event_venue') returning id")) {
                rows.next(); draftHallId = rows.getLong(1);
            }
            sql.executeUpdate("insert into venue_overture_imports(source_id,hall_id,catalog_version,release,category,"
                    + "source_operating_status,sources,created_by_admin) select '5ad382a2-632c-4d01-a5ce-0d6240e5f555',"
                    + draftHallId + ",'" + "0".repeat(64) + "','2026-09-23.1','event_venue','unknown',"
                    + "'[{\"dataset\":\"microsoft\",\"license\":\"CDLA-Permissive-2.0\",\"recordId\":\"synthetic-media\"}]'::jsonb,id "
                    + "from admins where email='admin4b@example.invalid'");
        }
        // V42 exists before startup, so the application specifically upgrades old drafts to V43.
        Flyway.configure().dataSource(url, "venue_app", "venue_app_password")
                .locations("classpath:db/migration").target("42").load().migrate();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "venue_app");
        registry.add("spring.datasource.password", () -> "venue_app_password");
        registry.add("app.overture-onboarding.media.bucket", () -> bucket);
    }

    @Autowired private OvertureDraftMediaService media;
    @Autowired private HallsService publicHalls;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private OvertureDraftMediaStorage storage;
    @MockitoBean private VenuePlacesClient places;

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(Long.toString(adminUserId), null, List.of());
    }
    private OvertureDraftMediaRequest.Upload upload(long version) {
        return new OvertureDraftMediaRequest.Upload(version, "Synthetic team photograph", SourceKind.TEAM_PHOTO, null,
                RightsBasis.TEAM_OWNED, null, "Generated test fixture; the test team owns this synthetic image", true);
    }
    private MockMultipartFile image() throws Exception {
        var picture = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        picture.setRGB(5, 5, 0xff2b9988);
        var bytes = new ByteArrayOutputStream(); assertTrue(ImageIO.write(picture, "png", bytes));
        return new MockMultipartFile("file", "synthetic.png", "image/png", bytes.toByteArray());
    }
    private Gallery gallery() { return media.gallery(draftHallId, auth()); }

    @Test @Order(1) void upgradeBackfillsPrivateStateWithoutChangingExistingData() {
        assertEquals(45, jdbc.queryForObject("select max(version::integer) from flyway_schema_history where success", Integer.class));
        storage.ensurePrivate();
        var result = gallery(); assertEquals(0, result.mediaVersion()); assertTrue(result.items().isEmpty());
        assertNull(result.coverMediaId()); assertEquals(0, result.retainedBytes());
        assertEquals(20, result.limits().maxActivePhotos());
        originalOwner = jdbc.queryForMap("select * from halls where id=?", ownerHallId);
        originalDraft = jdbc.queryForMap("select * from halls where id=?", draftHallId);
        originalSourceJson = jdbc.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?", String.class, draftHallId);
        businessCounts = List.of("users", "vendors", "hall_media", "bookings", "enquiries", "lead_notification_jobs")
                .stream().collect(java.util.stream.Collectors.toMap(table -> table, this::count));
    }

    @Test @Order(2) void uploadedPngBecomesPrivateCanonicalJpegWithImmutableRights() throws Exception {
        var result = media.upload(draftHallId, image(), upload(0), auth());
        assertEquals(1, result.mediaVersion()); assertEquals(1, result.activeCount());
        var photo = result.items().getFirst(); firstPhotoId = photo.id();
        assertEquals(PhotoStatus.PENDING, photo.status()); assertTrue(photo.rightsConfirmed());
        assertEquals("Media test admin", photo.uploadedBy().adminName());
        assertEquals(20, photo.width()); assertEquals(10, photo.height());
        firstStorageKey = jdbc.queryForObject("select storage_key from venue_overture_draft_photos where id=?", String.class, firstPhotoId);
        byte[] bytes = media.content(draftHallId, firstPhotoId, auth());
        assertEquals((byte) 0xff, bytes[0]); assertEquals((byte) 0xd8, bytes[1]);
        assertEquals(photo.sizeBytes(), bytes.length);
        assertEquals(photo.sha256(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:9000/"
                + System.getenv("OVERTURE_MEDIA_TEST_BUCKET") + "/" + firstStorageKey)).timeout(Duration.ofSeconds(5)).GET().build();
        int anonymousStatus = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        assertTrue(anonymousStatus == 403 || anonymousStatus == 404, "Private stored bytes must not allow anonymous downloads");
        byte[] altered = bytes.clone(); altered[altered.length - 1] ^= 1;
        doReturn(altered).when(storage).read(firstStorageKey, photo.sizeBytes());
        try {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                    () -> media.content(draftHallId, firstPhotoId, auth())).getStatusCode());
        } finally { doCallRealMethod().when(storage).read(firstStorageKey, photo.sizeBytes()); }
        assertThrows(RuntimeException.class, () -> jdbc.update(
                "update venue_overture_draft_photos set permission_evidence='Changed evidence' where id=?", firstPhotoId));
        assertThrows(RuntimeException.class, () -> jdbc.update("""
                update venue_overture_draft_photos set status='APPROVED',reviewed_by=uploaded_by,
                    reviewer_name=uploader_name,reviewed_at=now(),review_reason=null where id=?
                """, firstPhotoId), "Database review history must require a non-null reason");
        assertThrows(RuntimeException.class, () -> jdbc.update("""
                update venue_overture_draft_photos set status='ARCHIVED',archived_by=uploaded_by,
                    archiver_name=uploader_name,archived_at=now(),archive_reason=null where id=?
                """, firstPhotoId), "Database archive history must require a non-null reason");
        assertEquals(PhotoStatus.PENDING, gallery().items().getFirst().status());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> publicHalls.getPublicHall(Long.toString(draftHallId))).getStatusCode());
    }

    @Test @Order(3) void concurrentUploadsHaveExactlyOneWinnerAndIndependentFactsVersion() throws Exception {
        var stale = upload(0);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> media.upload(draftHallId, image(), stale, auth())).getStatusCode());
        var current = upload(gallery().mediaVersion());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.<Callable<Boolean>>of(() -> attempt(current), () -> attempt(current)));
            assertNotEquals(futures.get(0).get(), futures.get(1).get());
        }
        assertEquals(2, gallery().mediaVersion()); assertEquals(2, gallery().activeCount());
        assertEquals(0, jdbc.queryForObject("select review_version from venue_overture_draft_reviews where hall_id=?", Long.class, draftHallId));
    }

    @Test @Order(4) void reviewAndArrangementRemainPrivateAndPreserveReviewHistory() {
        var first = media.review(draftHallId, firstPhotoId,
                new OvertureDraftMediaRequest.Review(gallery().mediaVersion(), PhotoStatus.APPROVED, "Checked image and synthetic reuse evidence"), auth());
        var reviewed = first.items().stream().filter(item -> item.id() == firstPhotoId).findFirst().orElseThrow();
        assertEquals("Media test admin", reviewed.reviewedBy().adminName()); assertNotNull(reviewed.reviewedAt());
        long pendingId = first.items().stream().filter(item -> item.status() == PhotoStatus.PENDING).findFirst().orElseThrow().id();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> media.arrange(draftHallId,
                new OvertureDraftMediaRequest.Arrangement(first.mediaVersion(), pendingId, List.of(firstPhotoId, pendingId)), auth())).getStatusCode());
        var arranged = media.arrange(draftHallId,
                new OvertureDraftMediaRequest.Arrangement(first.mediaVersion(), firstPhotoId, List.of(firstPhotoId)), auth());
        assertEquals(firstPhotoId, arranged.coverMediaId());
        assertTrue(arranged.items().stream().filter(item -> item.id() == firstPhotoId).findFirst().orElseThrow().isCover());
        media.review(draftHallId, pendingId, new OvertureDraftMediaRequest.Review(arranged.mediaVersion(),
                PhotoStatus.REJECTED, "Synthetic rejection: not appropriate for this venue"), auth());
        assertThrows(ResponseStatusException.class, () -> media.review(draftHallId, firstPhotoId,
                new OvertureDraftMediaRequest.Review(gallery().mediaVersion(), PhotoStatus.REJECTED, "Reviewed records require archive and replacement"), auth()));
        assertEquals(reviewed.reviewReason(), gallery().items().stream().filter(item -> item.id() == firstPhotoId).findFirst().orElseThrow().reviewReason());
        assertEquals(originalDraft, jdbc.queryForMap("select * from halls where id=?", draftHallId));
    }

    @Test @Order(5) void auditFailureRollsBackMetadataAndCompensatesPrivateObject() throws Exception {
        var before = gallery(); long auditsBefore = count("audit_events");
        AtomicReference<String> uploadedKey = new AtomicReference<>();
        doAnswer(invocation -> { uploadedKey.set(invocation.getArgument(0)); return invocation.callRealMethod(); })
                .when(storage).put(anyString(), any(byte[].class), anyString());
        jdbc.execute("""
                create function slice4b_reject_audit() returns trigger language plpgsql as $$
                begin
                    if NEW.action = 'OVERTURE_DRAFT_PHOTO_UPLOADED' then raise exception 'Synthetic media audit failure'; end if;
                    return NEW;
                end $$
                """);
        jdbc.execute("create trigger slice4b_audit_failure before insert on audit_events for each row execute function slice4b_reject_audit()");
        try {
            assertThrows(RuntimeException.class, () -> media.upload(draftHallId, image(), upload(before.mediaVersion()), auth()));
            assertEquals(before, gallery()); assertEquals(auditsBefore, count("audit_events"));
            assertNotNull(uploadedKey.get());
            assertThrows(RuntimeException.class, () -> storage.read(uploadedKey.get(), OvertureDraftImageProcessor.MAX_OUTPUT_BYTES));
        } finally {
            jdbc.execute("drop trigger slice4b_audit_failure on audit_events");
            jdbc.execute("drop function slice4b_reject_audit()");
            doCallRealMethod().when(storage).put(anyString(), any(byte[].class), anyString());
        }
    }

    @Test @Order(6) void archiveRetainsEvidenceAndBytesButClearsPrivateCoverAndPreview() {
        var before = gallery(); var initial = before.items().stream().filter(item -> item.id() == firstPhotoId).findFirst().orElseThrow();
        var result = media.archive(draftHallId, firstPhotoId,
                new OvertureDraftMediaRequest.Archive(before.mediaVersion(), "Archived synthetic fixture after internal review"), auth());
        assertNull(result.coverMediaId()); assertEquals(1, result.activeCount());
        assertEquals(before.retainedBytes(), result.retainedBytes());
        var archived = result.items().stream().filter(item -> item.id() == firstPhotoId).findFirst().orElseThrow();
        assertEquals(PhotoStatus.ARCHIVED, archived.status()); assertFalse(archived.isCover());
        assertEquals(initial.permissionEvidence(), archived.permissionEvidence());
        assertEquals(initial.reviewedBy(), archived.reviewedBy()); assertEquals(initial.reviewedAt(), archived.reviewedAt());
        assertNotNull(archived.archivedAt()); assertEquals("Media test admin", archived.archivedBy().adminName());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> media.content(draftHallId, firstPhotoId, auth())).getStatusCode());
        assertEquals(initial.sizeBytes(), storage.read(firstStorageKey, OvertureDraftImageProcessor.MAX_OUTPUT_BYTES).length);
    }

    @Test @Order(7) void authorizationAndExistingBusinessDataStayIsolated() throws Exception {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> media.gallery(ownerHallId, auth())).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> media.gallery(draftHallId, null)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> media.content(ownerHallId,
                gallery().items().getLast().id(), auth())).getStatusCode());
        jdbc.update("update admins set status='INACTIVE' where email='admin4b@example.invalid'");
        try {
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> media.upload(draftHallId, image(), upload(galleryVersion()), auth())).getStatusCode());
        } finally { jdbc.update("update admins set status='ACTIVE' where email='admin4b@example.invalid'"); }
        assertEquals(originalOwner, jdbc.queryForMap("select * from halls where id=?", ownerHallId));
        assertEquals(originalDraft, jdbc.queryForMap("select * from halls where id=?", draftHallId));
        assertEquals(originalSourceJson, jdbc.queryForObject("select source_facts::text from venue_overture_draft_reviews where hall_id=?", String.class, draftHallId));
        for (var table : businessCounts.keySet()) assertEquals(businessCounts.get(table), count(table), table);
        verifyNoInteractions(places);
    }

    private boolean attempt(OvertureDraftMediaRequest.Upload upload) throws Exception {
        try { media.upload(draftHallId, image(), upload, auth()); return true; }
        catch (ResponseStatusException exception) { assertEquals(HttpStatus.CONFLICT, exception.getStatusCode()); return false; }
    }
    private long galleryVersion() {
        return jdbc.queryForObject("select media_version from venue_overture_media_state where hall_id=?", Long.class, draftHallId);
    }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
}
