package com.staminal.venue.overture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;

final class OvertureDraftMediaFixtures {
    static OvertureDraftMediaRequest.Upload upload() {
        return new OvertureDraftMediaRequest.Upload(0, "Hall exterior", SourceKind.TEAM_PHOTO, null,
                RightsBasis.TEAM_OWNED, null, "Captured by our team with documented usage rights", true);
    }
    static String json(Object value) {
        try { return new ObjectMapper().writeValueAsString(value); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
