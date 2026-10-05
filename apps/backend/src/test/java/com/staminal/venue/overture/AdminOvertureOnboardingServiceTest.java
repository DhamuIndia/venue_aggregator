package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.discovery.VenueDiscoveryAccess.Actor;

class AdminOvertureOnboardingServiceTest {
    private final VenueDiscoveryAccess access = mock(VenueDiscoveryAccess.class);
    private final OvertureCatalog catalog = mock(OvertureCatalog.class);
    private final OvertureOnboardingStore store = mock(OvertureOnboardingStore.class);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("1", null, List.of());
    private final Actor actor = new Actor(1L, "ADMIN", new Admin());
    private final OvertureCatalog.Snapshot snapshot = OvertureFixtures.snapshot();

    @BeforeEach void setup() {
        when(access.requireAdmin(auth)).thenReturn(actor);
        when(catalog.snapshot()).thenReturn(snapshot);
    }
    private AdminOvertureOnboardingService service(boolean enabled) {
        return new AdminOvertureOnboardingService(access, new OvertureOnboardingProperties(enabled, "", 20), catalog, store);
    }

    @Test void currentAdminIsCheckedBeforeCatalogOrStorageOnEveryEndpoint() {
        when(access.requireAdmin(auth)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        var service = service(true);
        assertThrows(ResponseStatusException.class, () -> service.settings(auth));
        assertThrows(ResponseStatusException.class, () -> service.catalog(0, 20, null, auth));
        assertThrows(ResponseStatusException.class, () -> service.preview(new OvertureRequest.Preview(List.of(OvertureFixtures.ID)), auth));
        assertThrows(ResponseStatusException.class, () -> service.importDrafts(new OvertureRequest.Import(snapshot.version(), List.of(OvertureFixtures.ID)), auth));
        assertThrows(ResponseStatusException.class, () -> service.drafts(0, 20, auth));
        verifyNoInteractions(store);
        verify(catalog, never()).snapshot();
    }

    @Test void disabledBlocksReadsAndWritesButSettingsExplainSafeState() {
        var service = service(false);
        assertFalse(service.settings(auth).ready());
        assertThrows(ResponseStatusException.class, () -> service.catalog(0, 20, null, auth));
        assertThrows(ResponseStatusException.class, () -> service.preview(new OvertureRequest.Preview(List.of(OvertureFixtures.ID)), auth));
        assertThrows(ResponseStatusException.class, () -> service.importDrafts(new OvertureRequest.Import(snapshot.version(), List.of(OvertureFixtures.ID)), auth));
        assertThrows(ResponseStatusException.class, () -> service.drafts(0, 20, auth));
        verifyNoInteractions(store);
    }

    @Test void staleCatalogVersionRejectsBeforeImportOrPreview() {
        var error = assertThrows(ResponseStatusException.class, () -> service(true).importDrafts(
                new OvertureRequest.Import("0".repeat(64), List.of(OvertureFixtures.ID)), auth));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(store);
    }

    @Test void invalidUnknownDuplicateAndOversizeSelectionCannotWrite() {
        var service = service(true);
        for (List<String> ids : List.of(List.<String>of(), List.of("bad-id"), List.of(OvertureFixtures.OTHER_ID),
                List.of(OvertureFixtures.ID, OvertureFixtures.ID), java.util.Collections.nCopies(21, OvertureFixtures.ID))) {
            assertThrows(ResponseStatusException.class, () -> service.preview(new OvertureRequest.Preview(ids), auth));
            assertThrows(ResponseStatusException.class, () -> service.importDrafts(new OvertureRequest.Import(snapshot.version(), ids), auth));
        }
        verifyNoInteractions(store);
    }

    @Test void validSelectionUsesOnlyCurrentCatalogAndAuthenticatedActor() {
        service(true).importDrafts(new OvertureRequest.Import(snapshot.version(), List.of(OvertureFixtures.ID)), auth);
        verify(store).importDrafts(snapshot, snapshot.venues(), actor);
    }

    @Test void nullCatalogFailsClosedWithGenericErrorButSavedDraftsRemainReadable() {
        when(catalog.snapshot()).thenReturn(null);
        assertFalse(service(true).settings(auth).ready());
        var error = assertThrows(ResponseStatusException.class, () -> service(true).catalog(0, 20, null, auth));
        assertEquals("Overture catalog is unavailable", error.getReason());
        service(true).drafts(0, 20, auth);
        verify(store).drafts(0, 20);
    }

    @Test void searchAndPaginationAreBoundedWithoutProviderCalls() {
        assertEquals(1, service(true).catalog(0, 20, "EXAMPLE", auth).totalElements());
        assertEquals(0, service(true).catalog(0, 20, "missing", auth).totalElements());
        assertEquals(0, service(true).catalog(100000, 50, null, auth).content().size());
        assertThrows(ResponseStatusException.class, () -> service(true).catalog(-1, 20, null, auth));
        assertThrows(ResponseStatusException.class, () -> service(true).catalog(0, 51, null, auth));
        assertThrows(ResponseStatusException.class, () -> service(true).catalog(0, 20, "q".repeat(121), auth));
        assertThrows(ResponseStatusException.class, () -> service(true).drafts(100001, 20, auth));
        verifyNoInteractions(store);
    }
}
