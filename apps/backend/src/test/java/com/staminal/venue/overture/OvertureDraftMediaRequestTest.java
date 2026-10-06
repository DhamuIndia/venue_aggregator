package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;

class OvertureDraftMediaRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void validCompleteRequestsHaveExactTypesAndNulls() throws Exception {
        var upload = OvertureDraftMediaFixtures.upload();
        assertEquals(upload, mapper.readValue(OvertureDraftMediaFixtures.json(upload), OvertureDraftMediaRequest.Upload.class));
        var arrange = new OvertureDraftMediaRequest.Arrangement(0, null, List.of());
        assertEquals(arrange, mapper.readValue(OvertureDraftMediaFixtures.json(arrange), OvertureDraftMediaRequest.Arrangement.class));
        var review = new OvertureDraftMediaRequest.Review(0, PhotoStatus.APPROVED, "Rights independently checked");
        assertEquals(review, mapper.readValue(OvertureDraftMediaFixtures.json(review), OvertureDraftMediaRequest.Review.class));
    }
    @ParameterizedTest @ValueSource(strings = {"id", "hallId", "storageKey", "status", "uploadedBy", "uploadedAt", "sha256", "url", "ownerUserId"})
    void uploadRejectsServerOwnedAndArbitraryFields(String field) throws Exception {
        ObjectNode json = mapper.valueToTree(OvertureDraftMediaFixtures.upload()); json.put(field, "injected");
        assertThrows(Exception.class, () -> mapper.treeToValue(json, OvertureDraftMediaRequest.Upload.class));
    }
    @Test void coercionDuplicateKeysTrailingTokensAndUnconfirmedRightsAreRejected() {
        String body = OvertureDraftMediaFixtures.json(OvertureDraftMediaFixtures.upload());
        for (String invalid : List.of(body + " {}", body.replace("\"expectedVersion\":0", "\"expectedVersion\":\"0\""),
                body.replace("\"expectedVersion\":0", "\"expectedVersion\":0,\"expectedVersion\":5"),
                body.replace("\"rightsConfirmed\":true", "\"rightsConfirmed\":false"),
                body.replace("\"rightsConfirmed\":true", "\"rightsConfirmed\":\"true\""),
                body.replace("\"caption\":\"Hall exterior\"", "\"caption\":5")))
            assertThrows(Exception.class, () -> mapper.readValue(invalid, OvertureDraftMediaRequest.Upload.class));
    }
    @ParameterizedTest @ValueSource(strings = {"PENDING", "ARCHIVED"})
    void reviewCannotSetLifecycleStatuses(String status) {
        assertThrows(Exception.class, () -> mapper.readValue("{\"expectedVersion\":0,\"status\":\"" + status + "\",\"reason\":\"Some clear reason\"}", OvertureDraftMediaRequest.Review.class));
    }
    @ParameterizedTest @ValueSource(strings = {"[1,1]", "[0]", "[-1]", "[\"1\"]", "[1.1]", "null"})
    void arrangementIdsAreUniquePositiveIntegralAndBounded(String ids) {
        assertThrows(Exception.class, () -> mapper.readValue("{\"expectedVersion\":0,\"coverMediaId\":null,\"orderedMediaIds\":" + ids + "}", OvertureDraftMediaRequest.Arrangement.class));
    }
    @Test void provenanceSourceRightsMustAgreeAndEvidenceIsNotAutomaticApproval() {
        assertEquals(OvertureDraftMediaFixtures.upload(), OvertureDraftMediaService.provenance(OvertureDraftMediaFixtures.upload()));
        assertThrows(Exception.class, () -> OvertureDraftMediaService.provenance(new OvertureDraftMediaRequest.Upload(0, null,
                SourceKind.BUSINESS_PROVIDED, null, RightsBasis.TEAM_OWNED, null, "Permission evidence", true)));
        assertThrows(Exception.class, () -> OvertureDraftMediaService.provenance(new OvertureDraftMediaRequest.Upload(0, null,
                SourceKind.LICENSED_IMAGE, null, RightsBasis.OPEN_LICENSE, "CC0", "Permission evidence", true)));
        assertThrows(Exception.class, () -> OvertureDraftMediaService.provenance(new OvertureDraftMediaRequest.Upload(0, null,
                SourceKind.LICENSED_IMAGE, "Licensed archive entry", RightsBasis.OPEN_LICENSE, null, "Permission evidence", true)));
        assertThrows(Exception.class, () -> OvertureDraftMediaService.evidence("short", "Evidence"));
        assertThrows(Exception.class, () -> OvertureDraftMediaService.evidence("x".repeat(4001), "Evidence"));
        assertDoesNotThrow(() -> OvertureDraftMediaService.provenance(new OvertureDraftMediaRequest.Upload(0, null,
                SourceKind.LICENSED_IMAGE, "Licensed archive entry", RightsBasis.OPEN_LICENSE, "CC BY 4.0", "Documented license and attribution checked", true)));
    }
    @ParameterizedTest @ValueSource(strings = {"https://maps.google.com/venue", "https://maps.app.goo.gl/abc", "https://lh3.googleusercontent.com/photo",
            "https://google.com/maps/place/venue", "https://google.com/%6daps/place/venue", "Google Maps screenshot",
            "https://%4daps.%47oogle.com/venue", "https://lh3.%47oogleusercontent.com/photo", "https://google.com/%4daps/place/venue"})
    void googlePhotoReferencesCannotBeIngested(String source) {
        assertThrows(Exception.class, () -> OvertureDraftMediaService.provenance(new OvertureDraftMediaRequest.Upload(0, null,
                SourceKind.LICENSED_IMAGE, source, RightsBasis.OPEN_LICENSE, "Claimed open license", "Permission evidence", true)));
    }
}
