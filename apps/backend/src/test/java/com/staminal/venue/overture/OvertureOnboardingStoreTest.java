package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess.Actor;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.HallListingOrigin;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.overture.OvertureResponse.*;

class OvertureOnboardingStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HallRepository halls = mock(HallRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final OvertureOnboardingStore store = new OvertureOnboardingStore(jdbc, halls, mapper, audit);
    private final CatalogVenue venue = OvertureFixtures.snapshot().venues().getFirst();

    @BeforeEach void setup() {
        when(halls.findAll()).thenReturn(List.of());
        when(halls.saveAndFlush(any())).thenAnswer(invocation -> {
            Halls value = invocation.getArgument(0); value.setId(55); return value;
        });
    }

    @Test void partialVenueCreatesUnownedDraftWithNoInventedMediaCapacityOrPricing() {
        Admin admin = new Admin(); admin.setId(4L);
        var result = store.importDrafts(OvertureFixtures.snapshot(), List.of(venue), new Actor(7L, "ADMIN", admin));
        assertEquals(1, result.createdCount());
        assertEquals(0, result.skippedCount());
        assertEquals(Outcome.CREATED, result.items().getFirst().outcome());
        assertEquals(55L, result.items().getFirst().hallId());
        var captor = org.mockito.ArgumentCaptor.forClass(Halls.class);
        verify(halls).saveAndFlush(captor.capture());
        Halls draft = captor.getValue();
        assertEquals(HallListingOrigin.APPLICATION, draft.getListingOrigin());
        assertEquals(HallStatus.DRAFT, draft.getStatus());
        assertNull(draft.getOwnerUserId()); assertNull(draft.getOwnerName());
        assertNull(draft.getCity()); assertNull(draft.getArea()); assertNull(draft.getAddressLine());
        assertNull(draft.getCoverImageUrl()); assertNull(draft.getCapacityMax()); assertNull(draft.getFullDayAmount());
        assertNull(draft.getApprovedBy()); assertNull(draft.getApprovedAt()); assertNull(draft.getWhatsappNumber());
        var auditCaptor = org.mockito.ArgumentCaptor.forClass(com.staminal.venue.audit.AuditCommand.class);
        verify(audit).record(auditCaptor.capture());
        assertEquals(AuditAction.OVERTURE_VENUE_DRAFT_CREATED, auditCaptor.getValue().action());
        assertFalse(auditCaptor.getValue().metadata().containsKey("phone"));
        var ordered = inOrder(jdbc, halls);
        ordered.verify(jdbc).queryForObject("select pg_advisory_xact_lock(?)", Object.class, OvertureOnboardingStore.IMPORT_LOCK);
        ordered.verify(halls).findAll();
        ordered.verify(halls).saveAndFlush(any());
    }

    @Test void repeatSourceImportReturnsExistingHallAndCreatesNothing() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(), eq(venue.id())))
                .thenReturn(List.of(77L));
        var preview = store.preview(List.of(venue)).getFirst();
        assertEquals(Outcome.ALREADY_IMPORTED, preview.outcome());
        assertEquals(77L, preview.hallId());
        var result = store.importDrafts(OvertureFixtures.snapshot(), List.of(venue), new Actor(7L, "ADMIN", new Admin()));
        assertEquals(0, result.createdCount());
        assertEquals(1, result.skippedCount());
        verify(halls, never()).saveAndFlush(any()); verifyNoInteractions(audit);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void possibleDuplicateDoesNotModifyExistingVenue() {
        Halls existing = new Halls(); existing.setId(10); existing.setLatitude(13.0001); existing.setLongitude(80.2001);
        existing.setName("Different name"); existing.setStatus(HallStatus.APPROVED);
        when(halls.findAll()).thenReturn(List.of(existing));
        var result = store.importDrafts(OvertureFixtures.snapshot(), List.of(venue), new Actor(7L, "ADMIN", new Admin()));
        assertEquals(Outcome.POSSIBLE_DUPLICATE, result.items().getFirst().outcome());
        assertNull(result.items().getFirst().hallId());
        assertEquals(HallStatus.APPROVED, existing.getStatus());
        verify(halls, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }

    @Test void closedLowConfidenceAndUnrecognizedStatusAreBlockedBeforeWrites() {
        for (CatalogVenue blocked : List.of(changed("permanently_closed", 0.95), changed("temporarily_closed", 0.95),
                changed("not-recognized", 0.95), changed("open", null), changed("open", 0.79))) {
            var result = store.importDrafts(OvertureFixtures.snapshot(), List.of(blocked), new Actor(7L, "ADMIN", new Admin()));
            assertEquals(Outcome.INCOMPLETE, result.items().getFirst().outcome());
            assertFalse(result.items().getFirst().issues().isEmpty());
        }
        verify(halls, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }

    @Test void unknownOperatingStatusCanBecomePrivateDraftWithExplicitVerificationGap() {
        for (CatalogVenue partial : java.util.Arrays.asList(changed("unknown", 0.95), changed(null, 0.95))) {
            var preview = store.preview(List.of(partial)).getFirst();
            assertEquals(Outcome.READY, preview.outcome());
            assertTrue(preview.issues().getFirst().contains("operatingStatus"));
            Halls draft = OvertureOnboardingStore.draft(partial);
            assertEquals(HallStatus.DRAFT, draft.getStatus());
            assertNull(draft.getApprovedAt());
        }
    }

    @Test void duplicateComparisonHandlesUnicodeSpacingCityAndSeventyFiveMeterRadius() {
        Halls hall = new Halls(); hall.setName("  EXAMPLE\u00a0EVENT   VENUE "); hall.setCity(" CHENNAI ");
        CatalogVenue named = new CatalogVenue(venue.id(), venue.name(), venue.category(), null, "Chennai", null, null,
                venue.latitude(), venue.longitude(), null, null, 0.95, "open", venue.sources());
        assertTrue(OvertureOnboardingStore.possibleDuplicate(named, hall));
        hall.setCity("Madurai"); assertFalse(OvertureOnboardingStore.possibleDuplicate(named, hall));
        hall.setLatitude(13.0001); hall.setLongitude(80.2001); assertTrue(OvertureOnboardingStore.possibleDuplicate(venue, hall));
        hall.setLatitude(13.1); hall.setLongitude(80.3); assertFalse(OvertureOnboardingStore.possibleDuplicate(venue, hall));
        assertEquals(HallListingOrigin.OWNER, new Halls().getListingOrigin());
    }

    private CatalogVenue changed(String status, Double confidence) {
        return new CatalogVenue(venue.id(), venue.name(), venue.category(), venue.address(), venue.city(), venue.area(),
                venue.postcode(), venue.latitude(), venue.longitude(), venue.phone(), venue.website(), confidence, status, venue.sources());
    }

    @Test void draftTimestampIsSerializedWithUtcOffset() throws Exception {
        var timestamp = java.time.Instant.parse("2026-10-05T01:00:00Z");
        var draft = new Draft(55, venue.id(), venue.name(), null, null, null, venue.category(),
                "2026-09-23.1", timestamp, venue.sources(), List.of(), "DRAFT");
        var serializer = mapper.copy().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var json = serializer.readTree(serializer.writeValueAsBytes(draft));
        assertEquals("2026-10-05T01:00:00Z", json.path("importedAt").asText());
    }
}
