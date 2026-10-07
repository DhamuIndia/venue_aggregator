package com.staminal.venue.overture;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.overture.OvertureDraftReviewResponse.*;

final class OvertureDraftReviewTestFixtures {
    static Facts facts() {
        return new Facts("Example Event Venue", "1 Sample Street", "Chennai", "Adyar", "600020", 13.0, 80.2,
                "+919999999999", "https://example.org", "open", 250, "Venue notes",
                new Amenities(null, null, null, null, null, null, null, null));
    }
    static OvertureDraftReviewRequest.Update update() {
        return new OvertureDraftReviewRequest.Update(0, facts(), List.of(), ReviewStatus.IN_REVIEW, null,
                DuplicateDecision.NOT_REVIEWED, null, List.of());
    }
    static String json() {
        try { return new ObjectMapper().writeValueAsString(update()); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
