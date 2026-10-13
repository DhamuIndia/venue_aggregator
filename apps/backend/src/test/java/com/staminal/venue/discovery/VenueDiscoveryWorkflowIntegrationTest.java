package com.staminal.venue.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
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
import com.staminal.venue.discovery.places.PlacePreview;
import com.staminal.venue.discovery.places.VenuePlacesClient;

/** Opt-in only: use a new disposable local database with the required name prefix. */
@EnabledIfEnvironmentVariable(named = "VENUE_DISCOVERY_TEST_DB",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/venuemart_phase2_test_[a-zA-Z0-9_]+")
@SpringBootTest(properties = {
        "app.features.venue-discovery-enabled=true", "app.venue-discovery.live-api-enabled=true",
        "app.venue-discovery.google-api-key=mocked-test-only", "app.venue-discovery.daily-request-limit=5",
        "app.notifications.whatsapp.sending-enabled=false", "app.notifications.whatsapp.webhook-enabled=false"
})
class VenueDiscoveryWorkflowIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("VENUE_DISCOVERY_TEST_DB"));
        registry.add("spring.datasource.username", () -> "venue_app");
        registry.add("spring.datasource.password", () -> "venue_app_password");
    }

    @Autowired private AdminVenueDiscoveryService service;
    @Autowired private VenueDiscoveryQuota quota;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private VenuePlacesClient places;

    @Test void repeatedDiscoveryReviewFailureAndConcurrentQuotaPreserveBusinessData() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String email = "discovery-" + tag + "@example.invalid";
        Long userId = jdbc.queryForObject("""
                insert into users (full_name, phone, email, password_hash)
                values ('Discovery test admin', ?, ?, 'not-a-real-password') returning id
                """, Long.class, "test" + tag, email);
        jdbc.update("insert into roles (name) values ('ADMIN') on conflict (name) do nothing");
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name='ADMIN'", userId);
        jdbc.update("""
                insert into admins (full_name, email, contact_number, password_hash)
                values ('Discovery test admin', ?, ?, 'not-a-real-password')
                """, email, "test" + tag);
        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
        long hallsBefore = count("halls");
        long vendorsBefore = count("vendors");
        long jobsBefore = count("lead_notification_jobs");
        String placeId = "test_place_" + tag;
        var preview = new PlacePreview(placeId, "Synthetic venue preview", "Synthetic address only",
                "OPERATIONAL", "https://www.google.com/maps?cid=123", List.of());
        when(places.search(anyString(), anyInt())).thenReturn(List.of(preview));
        when(places.details(placeId)).thenReturn(preview);
        var request = new VenueDiscoveryRequest.Search("Chennai", "Adyar", VenueDiscoveryVenueType.WEDDING_HALL);
        var first = service.search(request, auth);
        assertEquals("COMPLETED", first.run().status());
        Long candidateId = first.candidates().getFirst().id();
        service.review(candidateId, new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.SHORTLISTED,
                VenueDiscoveryCandidateStatus.DISCOVERED), auth);
        var second = service.search(request, auth);
        assertEquals(candidateId, second.candidates().getFirst().id());
        assertEquals("SHORTLISTED", second.candidates().getFirst().status());
        assertEquals(1, jdbc.queryForObject("select count(*) from venue_discovery_candidates where source_place_id=?",
                Integer.class, placeId));
        assertEquals(2, jdbc.queryForObject("select count(*) from venue_discovery_run_candidates where candidate_id=?",
                Integer.class, candidateId));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.review(candidateId,
                new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.REJECTED,
                        VenueDiscoveryCandidateStatus.DISCOVERED), auth)).getStatusCode());
        assertEquals(1, service.candidates(VenueDiscoveryCandidateStatus.SHORTLISTED, 0, 20, auth).totalElements());
        assertEquals(1, service.run(first.run().id(), auth).candidates().size());
        assertEquals(preview, service.preview(candidateId, auth));
        when(places.search(anyString(), anyInt())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Safe provider failure"));
        assertThrows(ResponseStatusException.class, () -> service.search(request, auth));
        assertEquals(1, jdbc.queryForObject("select count(*) from venue_discovery_runs where status='FAILED'", Integer.class));
        assertEquals(4, quota.usedToday());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.<java.util.concurrent.Callable<Boolean>>of(this::tryReservation, this::tryReservation);
            var futures = executor.invokeAll(tasks);
            int successes = 0;
            for (var future : futures) { if (future.get()) successes++; }
            assertEquals(1, successes, "Only one reservation remains, even across concurrent transactions");
        }
        assertEquals(5, quota.usedToday());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.search(request, auth)).getStatusCode());
        verify(places, times(3)).search(anyString(), anyInt());
        assertEquals(hallsBefore, count("halls"));
        assertEquals(vendorsBefore, count("vendors"));
        assertEquals(jobsBefore, count("lead_notification_jobs"));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from audit_events where
                coalesce(new_values, '') like '%Synthetic%' or coalesce(metadata, '') like '%Synthetic%'
                """, Integer.class));
    }

    private boolean tryReservation() {
        try { quota.reserve(); return true; }
        catch (ResponseStatusException e) { assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode()); return false; }
    }

    private long count(String table) {
        // Table names are hard-coded in this test, never user-controlled.
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
