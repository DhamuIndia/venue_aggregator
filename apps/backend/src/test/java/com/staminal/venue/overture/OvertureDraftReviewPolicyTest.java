package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.overture.OvertureDraftReviewResponse.*;

class OvertureDraftReviewPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Facts facts = OvertureDraftReviewTestFixtures.facts();
    private final Instant now = Instant.parse("2026-10-05T06:00:00Z");

    @Test void falseIsKnownAndVerifiableButNullOrUnknownIsNot() {
        var falseFacts = new Facts(facts.name(), facts.address(), facts.city(), facts.area(), facts.postcode(), facts.latitude(), facts.longitude(),
                facts.phone(), facts.website(), facts.operatingStatus(), facts.capacity(), facts.description(),
                new Amenities(false, null, null, null, null, null, null, null));
        assertFalse(OvertureDraftReviewPolicy.missing("amenities.ac", false));
        assertTrue(OvertureDraftReviewPolicy.missing("operatingStatus", "unknown"));
        var verified = OvertureDraftReviewPolicy.verifications(facts, falseFacts, Map.of(), List.of("amenities.ac"), "Site visit", 7, "Admin", now);
        assertEquals("Site visit", verified.get("amenities.ac").evidence());
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.verifications(facts, facts, Map.of(),
                List.of("amenities.ac"), "Site visit", 7, "Admin", now));
    }
    @Test void unchangedVerificationKeepsOriginalActorAndEvidenceAndUncheckedIsRemoved() {
        Verification old = new Verification(3, "Earlier Admin", now.minusSeconds(3600), "Earlier evidence");
        var retained = OvertureDraftReviewPolicy.verifications(facts, facts, Map.of("name", old), List.of("name"), null, 7, "Admin", now);
        assertEquals(old, retained.get("name"));
        assertTrue(OvertureDraftReviewPolicy.verifications(facts, facts, Map.of("name", old), List.of(), null, 7, "Admin", now).isEmpty());
    }
    @Test void changedFactCannotKeepOldVerificationWithoutFreshEvidence() {
        Verification old = new Verification(3, "Earlier Admin", now.minusSeconds(3600), "Earlier evidence");
        Facts changed = new Facts("Changed Venue", facts.address(), facts.city(), facts.area(), facts.postcode(), facts.latitude(), facts.longitude(),
                facts.phone(), facts.website(), facts.operatingStatus(), facts.capacity(), facts.description(), facts.amenities());
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.verifications(facts, changed, Map.of("name", old),
                List.of("name"), null, 7, "Admin", now));
        var refreshed = OvertureDraftReviewPolicy.verifications(facts, changed, Map.of("name", old), List.of("name"), "Rechecked", 7, "Admin", now);
        assertEquals(7, refreshed.get("name").adminId()); assertEquals(now, refreshed.get("name").verifiedAt());
    }
    @Test void onlyTheBoundedFactFieldsCanBeVerified() {
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.verifications(facts, facts, Map.of(),
                List.of("status"), "Evidence", 7, "Admin", now));
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.verifications(facts, facts, Map.of(),
                List.of("name", "name"), "Evidence", 7, "Admin", now));
    }
    @Test void verifiedRequiresAllMinimumFactsOpenAndEvidence() {
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireVerified(facts, Map.of(), ReviewStatus.VERIFIED));
        var verification = OvertureDraftReviewPolicy.verifications(facts, facts, Map.of(), OvertureDraftReviewPolicy.REQUIRED,
                "Site visit", 7, "Admin", now);
        assertDoesNotThrow(() -> OvertureDraftReviewPolicy.requireVerified(facts, verification, ReviewStatus.VERIFIED));
        Facts closed = new Facts(facts.name(), facts.address(), facts.city(), facts.area(), facts.postcode(), facts.latitude(), facts.longitude(),
                facts.phone(), facts.website(), "temporarily_closed", facts.capacity(), facts.description(), facts.amenities());
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireVerified(closed, verification, ReviewStatus.VERIFIED));
        assertDoesNotThrow(() -> OvertureDraftReviewPolicy.requireVerified(closed, Map.of(), ReviewStatus.IN_REVIEW));
    }
    @Test void duplicateAssessmentRequiresExactCurrentMatchesAndCannotConfirmNone() {
        var duplicates = List.of(new Duplicate(10, "Venue", "Chennai", "Adyar", "APPROVED", "OWNER", 10d));
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.VERIFIED,
                DuplicateDecision.NOT_REVIEWED, null, List.of(), duplicates, false));
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.VERIFIED,
                DuplicateDecision.DISTINCT, "Not the same venue", List.of(11L), duplicates, false));
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.VERIFIED,
                DuplicateDecision.DISTINCT, null, List.of(10L), duplicates, false));
        assertDoesNotThrow(() -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.VERIFIED,
                DuplicateDecision.DISTINCT, "Separate hall", List.of(10L), duplicates, false));
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.DUPLICATE,
                DuplicateDecision.CONFIRMED_DUPLICATE, "Same venue", List.of(), List.of(), false));
        assertDoesNotThrow(() -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.DUPLICATE,
                DuplicateDecision.CONFIRMED_DUPLICATE, "Same venue", List.of(10L), duplicates, false));
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.VERIFIED,
                DuplicateDecision.DISTINCT, "Separate hall", List.of(10L), duplicates, true));
    }

    @Test void staleAcknowledgedMatchesAreConflictButMissingNotesRemainBadRequest() {
        var duplicates = List.of(new Duplicate(10, "Venue", "Chennai", "Adyar", "APPROVED", "OWNER", 10d));
        var stale = assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.VERIFIED,
                DuplicateDecision.DISTINCT, "Old assessment", List.of(11L), duplicates, false));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, stale.getStatusCode());
        assertTrue(stale.getReason().contains("reload"));
        var missingNotes = assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.requireDecision(ReviewStatus.VERIFIED,
                DuplicateDecision.DISTINCT, null, List.of(11L), duplicates, false));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, missingNotes.getStatusCode());
    }
    @ParameterizedTest @ValueSource(strings = {"javascript:alert(1)", "data:text/html,hello", "https://u:p@example.org", "https:///path",
            "https://example.org/%0d%0aHeader", "https://example.org:65536", "https://example.org/\u200btest"})
    void unsafeWebsitesAreRejected(String website) {
        Facts invalid = new Facts(facts.name(), facts.address(), facts.city(), facts.area(), facts.postcode(), facts.latitude(), facts.longitude(),
                facts.phone(), website, facts.operatingStatus(), facts.capacity(), facts.description(), facts.amenities());
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.normalize(invalid));
    }
    @Test void pairedFiniteCoordinatesAndCapacityBoundsAreEnforced() {
        for (Facts invalid : List.of(withCoordinates(null, 80.2, 250), withCoordinates(Double.NaN, 80.2, 250),
                withCoordinates(91d, 80.2, 250), withCoordinates(13d, -181d, 250), withCoordinates(13d, 80.2, 0), withCoordinates(13d, 80.2, 100001)))
            assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.normalize(invalid));
        assertDoesNotThrow(() -> OvertureDraftReviewPolicy.normalize(withCoordinates(null, null, null)));
    }
    @Test void whitespaceNormalizesToMissingAndUnknownOperatingStatusIsExplicit() {
        Facts partial = new Facts(" Example ", "  ", null, null, null, null, null, null, null, null, null, null, facts.amenities());
        Facts normalized = OvertureDraftReviewPolicy.normalize(partial);
        assertEquals("Example", normalized.name()); assertNull(normalized.address()); assertEquals("unknown", normalized.operatingStatus());
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.text("x".repeat(121), 120, false));
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.text("a\u0000b", 120, false));
        assertEquals("example event venue", OvertureOnboardingStore.fold("\tEXAMPLE\u00a0EVENT  VENUE\n"));
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "12345", "++919876123450", "919876123450 ext3", "http://example.org", "919876123450\u0000"})
    void invalidPhoneFactsCannotBeSubmitted(String phone) {
        Facts invalid = new Facts(facts.name(), facts.address(), facts.city(), facts.area(), facts.postcode(), facts.latitude(), facts.longitude(),
                phone, facts.website(), facts.operatingStatus(), facts.capacity(), facts.description(), facts.amenities());
        assertThrows(ResponseStatusException.class, () -> OvertureDraftReviewPolicy.normalize(invalid));
    }
    @Test void strictRequestAcceptsCompleteFactsAndRejectsAuthorityAndScalarCoercions() throws Exception {
        String json = OvertureDraftReviewTestFixtures.json();
        assertEquals(facts, mapper.readValue(json, OvertureDraftReviewRequest.Update.class).facts());
        var base = mapper.readTree(json);
        for (String field : List.of("status", "ownerUserId", "listingOrigin", "price", "coverImageUrl", "paymentStatus")) {
            var copy = base.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) copy.get("facts")).put(field, "APPROVED");
            assertThrows(Exception.class, () -> mapper.treeToValue(copy, OvertureDraftReviewRequest.Update.class));
        }
        for (String invalid : List.of(json + " {}", json + " null", json.replace("\"expectedVersion\":0", "\"expectedVersion\":\"0\""),
                json.replace("\"capacity\":250", "\"capacity\":250.5"),
                json.replace("\"latitude\":13.0", "\"latitude\":\"13.0\""),
                json.replace("\"ac\":null", "\"ac\":\"false\""),
                json.replace("\"ac\":null,", ""), json.replace("\"verifiedFields\":[]", "\"verifiedFields\":[\"name\",\"name\"]")))
            assertThrows(Exception.class, () -> mapper.readValue(invalid, OvertureDraftReviewRequest.Update.class));
    }
    private Facts withCoordinates(Double latitude, Double longitude, Integer capacity) {
        return new Facts(facts.name(), facts.address(), facts.city(), facts.area(), facts.postcode(), latitude, longitude,
                facts.phone(), facts.website(), facts.operatingStatus(), capacity, facts.description(), facts.amenities());
    }
}
