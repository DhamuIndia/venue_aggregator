package com.staminal.venue.overture;

import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

final class OvertureFixtures {
    static final String ID = "69f6ec28-b302-4c7f-9bd2-c681ed502901";
    static final String OTHER_ID = "69f6ec28-b302-4c7f-9bd2-c681ed502902";
    static String json() {
        return """
                {"schemaVersion":1,"provider":"OVERTURE_PLACES","release":"2026-09-23.1",
                 "generatedAt":"2026-10-05T06:00:00Z","region":{"city":"Chennai","bbox":[80.0,12.8,80.4,13.3]},
                 "venues":[{"id":"69f6ec28-b302-4c7f-9bd2-c681ed502901","name":"Example Event Venue",
                   "category":"event_venue","address":null,"city":null,"area":null,"postcode":null,
                   "latitude":13.0,"longitude":80.2,"phone":null,"website":"https://example.org/venue",
                   "confidence":0.95,"operatingStatus":"open",
                   "sources":[{"dataset":"meta","license":"CDLA-Permissive-2.0","recordId":"sample-1"}]}]}
                """;
    }
    static OvertureCatalog.Snapshot snapshot() {
        try { return OvertureCatalog.parse(json().getBytes(StandardCharsets.UTF_8), new ObjectMapper()); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
