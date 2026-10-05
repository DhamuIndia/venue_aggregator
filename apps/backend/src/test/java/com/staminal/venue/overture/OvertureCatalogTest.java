package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.databind.ObjectMapper;

class OvertureCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path directory;

    @Test void validSnapshotIsImmutableAndDoesNotInventMissingLocationOrContacts() {
        var snapshot = OvertureFixtures.snapshot();
        assertTrue(snapshot.version().matches("[a-f0-9]{64}"));
        assertEquals("Chennai", snapshot.city());
        var venue = snapshot.venues().getFirst();
        assertNull(venue.city());
        assertNull(venue.area());
        assertNull(venue.address());
        assertNull(venue.phone());
        assertEquals("event_venue", venue.category());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.venues().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.byId().clear());
        assertThrows(UnsupportedOperationException.class, () -> venue.sources().clear());
    }

    @Test void catalogIsLoadedOnceAndVersionsCoverExactBytes() throws Exception {
        Path file = directory.resolve("catalog.json");
        Files.writeString(file, OvertureFixtures.json());
        var catalog = new OvertureCatalog(new OvertureOnboardingProperties(true, file.toString(), 20), mapper);
        String version = catalog.snapshot().version();
        Files.writeString(file, "broken replacement");
        assertEquals(version, catalog.snapshot().version());
        var other = parse(OvertureFixtures.json() + " ");
        assertNotEquals(version, other.version());
    }

    @Test void disabledMissingInvalidAndOversizeCatalogsFailClosed() throws Exception {
        Path file = directory.resolve("catalog.json");
        Files.writeString(file, OvertureFixtures.json());
        assertNull(new OvertureCatalog(new OvertureOnboardingProperties(false, file.toString(), 20), mapper).snapshot());
        assertNull(new OvertureCatalog(new OvertureOnboardingProperties(true, "", 20), mapper).snapshot());
        assertNull(new OvertureCatalog(new OvertureOnboardingProperties(true, directory.resolve("missing").toString(), 20), mapper).snapshot());
        Files.writeString(file, "bad JSON");
        assertNull(new OvertureCatalog(new OvertureOnboardingProperties(true, file.toString(), 20), mapper).snapshot());
        assertThrows(IllegalArgumentException.class, () -> OvertureCatalog.parse(new byte[OvertureCatalog.MAX_BYTES + 1], mapper));
    }

    @ParameterizedTest @MethodSource("invalidCatalogs")
    void invalidDataAndUnlicensedSourcesInvalidateWholeSnapshot(String json) {
        assertThrows(Exception.class, () -> parse(json));
    }

    static Stream<String> invalidCatalogs() {
        String base = OvertureFixtures.json();
        return Stream.of(base.replace("\"schemaVersion\":1", "\"schemaVersion\":4294967297"),
                base.replace("2026-09-23.1", "2026-02-31.1"),
                base.replace("OVERTURE_PLACES", "GOOGLE_PLACES"),
                base.replace("event_venue", "wedding_venue"),
                base.replace("\"meta\"", "\"unknown-provider\""),
                base.replace("CDLA-Permissive-2.0", "Apache-2.0"),
                base.replace("Example Event Venue", "Example\\nVenue"),
                base.replace("Example Event Venue", "Example\\u202eVenue"),
                base.replace("Example Event Venue", "x".repeat(181)),
                base.replace("https://example.org/venue", "javascript:alert(1)"),
                base.replace("https://example.org/venue", "https://user:pass@example.org/"),
                base.replace("\"confidence\":0.95", "\"confidence\":1.1"),
                base.replace("\"latitude\":13.0", "\"latitude\":40.0"),
                base.replace("[80.0,12.8,80.4,13.3]", "[80.4,12.8,80.0,13.3]"),
                base.replace("\"name\":\"Example Event Venue\"", "\"name\":\"Example Event Venue\",\"name\":\"Other\""),
                base.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"unexpected\":true"), base + "{}");
    }

    @Test void missingConfidenceAndUnknownStatusAreParsedForReviewRatherThanInvented() throws Exception {
        var snapshot = parse(OvertureFixtures.json().replace("\"confidence\":0.95", "\"confidence\":null")
                .replace("\"operatingStatus\":\"open\"", "\"operatingStatus\":\"unknown\""));
        assertNull(snapshot.venues().getFirst().confidence());
        assertEquals("unknown", snapshot.venues().getFirst().operatingStatus());
    }

    @Test void officialZeroAndTwoDigitReleaseRevisionsAreValidButDatesAndSuffixesRemainStrict() throws Exception {
        assertEquals("2026-09-23.0", parse(OvertureFixtures.json().replace("2026-09-23.1", "2026-09-23.0")).release());
        assertEquals("2026-09-23.12", parse(OvertureFixtures.json().replace("2026-09-23.1", "2026-09-23.12")).release());
        for (String release : java.util.List.of("2026-02-31.0", "2026-09-23.-1", "2026-09-23.123", "2026-09-23.0/other"))
            assertThrows(Exception.class, () -> parse(OvertureFixtures.json().replace("2026-09-23.1", release)));
    }

    @Test void overtureFieldProvenanceRequiresItsExplicitPermittedLicense() throws Exception {
        String json = OvertureFixtures.json().replace("\"dataset\":\"meta\"", "\"dataset\":\"overture\"");
        assertEquals("overture", parse(json).venues().getFirst().sources().getFirst().dataset());
        assertThrows(Exception.class, () -> parse(json.replace("\"license\":\"CDLA-Permissive-2.0\"", "\"license\":null")));
        assertThrows(Exception.class, () -> parse(json.replace("CDLA-Permissive-2.0", "CC0-1.0")));
        assertNull(parse(json.replace("\"recordId\":\"sample-1\"", "\"recordId\":null"))
                .venues().getFirst().sources().getFirst().recordId());
    }

    @Test @EnabledIfEnvironmentVariable(named = "OVERTURE_TEST_CATALOG_PATH", matches = ".+")
    void preparedRegionalSourceFilePassesTheSameRuntimeValidation() {
        var catalog = new OvertureCatalog(new OvertureOnboardingProperties(true, System.getenv("OVERTURE_TEST_CATALOG_PATH"), 20), mapper);
        assertNotNull(catalog.snapshot(), "Prepared catalog must pass the exact runtime loader");
        assertFalse(catalog.snapshot().venues().isEmpty(), "A regional coverage check must have usable results");
        assertTrue(catalog.snapshot().venues().size() <= OvertureCatalog.MAX_RECORDS);
        assertTrue(catalog.snapshot().venues().stream().allMatch(venue -> !venue.sources().isEmpty()));
    }

    @Test void duplicateIdsAndTooManyRecordsFailClosed() throws Exception {
        var root = mapper.readTree(OvertureFixtures.json());
        var records = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("venues");
        records.add(records.get(0).deepCopy());
        assertThrows(Exception.class, () -> parse(mapper.writeValueAsString(root)));
        records.removeAll();
        for (int i = 0; i <= OvertureCatalog.MAX_RECORDS; i++) records.addNull();
        assertThrows(Exception.class, () -> parse(mapper.writeValueAsString(root)));
    }

    private OvertureCatalog.Snapshot parse(String json) throws Exception {
        return OvertureCatalog.parse(json.getBytes(StandardCharsets.UTF_8), mapper);
    }
}
