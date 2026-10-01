package com.staminal.venue.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.discovery.VenueDiscoveryAccess.Actor;
import com.staminal.venue.discovery.VenueDiscoveryResponse.Run;
import com.staminal.venue.discovery.VenueDiscoveryResponse.RunDetail;
import com.staminal.venue.discovery.places.PlacePreview;
import com.staminal.venue.discovery.places.VenuePlacesClient;

class AdminVenueDiscoveryServiceTest {
    private final VenueDiscoveryAccess access = mock(VenueDiscoveryAccess.class);
    private final VenueDiscoveryQuota quota = mock(VenueDiscoveryQuota.class);
    private final VenueDiscoveryStore store = mock(VenueDiscoveryStore.class);
    private final VenuePlacesClient places = mock(VenuePlacesClient.class);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("1", null, List.of());
    private final Actor actor = new Actor(1L, "ADMIN", new Admin());
    private final VenueDiscoveryRequest.Search search = new VenueDiscoveryRequest.Search(
            " Chennai ", " Adyar ", VenueDiscoveryVenueType.WEDDING_HALL);

    @BeforeEach void setup() { when(access.requireAdmin(auth)).thenReturn(actor); }

    private AdminVenueDiscoveryService service(boolean enabled, boolean live, String key) {
        return new AdminVenueDiscoveryService(access,
                new VenueDiscoveryProperties(enabled, live, key, 10, 100, 10), quota, store, places);
    }

    @Test void disabledBlocksAllWorkBeforeQuotaOrProvider() {
        var service = service(false, true, "test");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service.search(search, auth)).getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.preview(1L, auth));
        assertThrows(ResponseStatusException.class, () -> service.runs(0, 20, auth));
        assertThrows(ResponseStatusException.class, () -> service.candidates(null, 0, 20, auth));
        assertThrows(ResponseStatusException.class, () -> service.run(1L, auth));
        assertThrows(ResponseStatusException.class, () -> service.review(1L,
                new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.SHORTLISTED,
                        VenueDiscoveryCandidateStatus.DISCOVERED), auth));
        verifyNoInteractions(quota, store, places);
    }

    @Test void liveSwitchAndKeyEachRequired() {
        assertThrows(ResponseStatusException.class, () -> service(true, false, "test").search(search, auth));
        assertThrows(ResponseStatusException.class, () -> service(true, true, "").search(search, auth));
        verifyNoInteractions(quota, store, places);
    }

    @Test void unauthorizedChecksPrecedeAnySideEffects() {
        when(access.requireAdmin(auth)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class, () -> service(true, true, "test").search(search, auth));
        verifyNoInteractions(quota, store, places);
    }

    @Test void quotaFailurePreventsRunAndNetwork() {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).when(quota).reserve();
        assertThrows(ResponseStatusException.class, () -> service(true, true, "test").search(search, auth));
        verifyNoInteractions(store, places);
    }

    @Test void providerContentOnlyAppearsInTransientResponse() {
        when(store.start(search, actor)).thenReturn(5L);
        var preview = new PlacePreview("place_1", "Example", "Example address", "OPERATIONAL",
                "https://maps.google.com/?cid=1", List.of());
        when(places.search("Wedding halls in Adyar, Chennai, India", 10)).thenReturn(List.of(preview));
        var run = new Run(5L, "Chennai", "Adyar", "WEDDING_HALL", "COMPLETED", Instant.now(),
                Instant.now(), Instant.now(), null, 1);
        when(store.complete(5L, List.of("place_1"), actor)).thenReturn(new RunDetail(run, List.of()));
        var response = service(true, true, "test").search(search, auth);
        assertEquals(List.of(preview), response.previews());
        var order = inOrder(quota, store, places);
        order.verify(quota).reserve();
        order.verify(store).start(search, actor);
        order.verify(places).search(anyString(), eq(10));
        order.verify(store).complete(5L, List.of("place_1"), actor);
    }

    @Test void failuresPersistSanitizedRunFailureWithoutRetries() {
        when(store.start(search, actor)).thenReturn(6L);
        when(places.search(anyString(), anyInt())).thenThrow(new IllegalStateException("secret-provider-payload"));
        var error = assertThrows(ResponseStatusException.class, () -> service(true, true, "test").search(search, auth));
        assertFalse(error.getMessage().contains("secret-provider-payload"));
        verify(store).fail(6L);
        verify(places, times(1)).search(anyString(), anyInt());
    }

    @Test void historyAndReviewsDoNotRequireLiveGoogle() {
        var service = service(true, false, "");
        service.runs(0, 20, auth);
        service.candidates(VenueDiscoveryCandidateStatus.SHORTLISTED, 0, 20, auth);
        service.run(5L, auth);
        var review = new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.REJECTED,
                VenueDiscoveryCandidateStatus.DISCOVERED);
        service.review(2L, review, auth);
        verify(store).review(2L, review, actor);
        verifyNoInteractions(quota, places);
    }

    @Test void explicitPreviewChecksExistenceBeforeChargingQuota() {
        when(store.placeId(3L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThrows(ResponseStatusException.class, () -> service(true, true, "test").preview(3L, auth));
        verifyNoInteractions(quota, places);
    }

    @Test void paginationIsBounded() {
        var service = service(true, false, "");
        assertThrows(ResponseStatusException.class, () -> service.runs(-1, 20, auth));
        assertThrows(ResponseStatusException.class, () -> service.candidates(null, 0, 51, auth));
        verifyNoInteractions(store);
    }
}
