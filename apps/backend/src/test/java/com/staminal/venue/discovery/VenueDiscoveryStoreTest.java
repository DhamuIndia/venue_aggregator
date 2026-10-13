package com.staminal.venue.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess.Actor;

class VenueDiscoveryStoreTest {
    private final VenueDiscoveryRunRepository runs = mock(VenueDiscoveryRunRepository.class);
    private final VenueDiscoveryCandidateRepository candidates = mock(VenueDiscoveryCandidateRepository.class);
    private final VenueDiscoveryRunCandidateRepository observations = mock(VenueDiscoveryRunCandidateRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);
    private final VenueDiscoveryStore store = new VenueDiscoveryStore(runs, candidates, observations, jdbc, audit);
    private final Actor actor = new Actor(1L, "ADMIN", new Admin());

    private VenueDiscoveryCandidate candidate(VenueDiscoveryCandidateStatus status) {
        var candidate = new VenueDiscoveryCandidate();
        candidate.setId(2L);
        candidate.setSourcePlaceId("place_2");
        candidate.setStatus(status);
        candidate.onCreate();
        return candidate;
    }

    @Test void reviewRequiresExpectedStatusAndLocksRecord() {
        var candidate = candidate(VenueDiscoveryCandidateStatus.SHORTLISTED);
        when(candidates.findByIdForUpdate(2L)).thenReturn(Optional.of(candidate));
        var error = assertThrows(ResponseStatusException.class, () -> store.review(2L,
                new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.REJECTED,
                        VenueDiscoveryCandidateStatus.DISCOVERED), actor));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(VenueDiscoveryCandidateStatus.SHORTLISTED, candidate.getStatus());
        verifyNoInteractions(audit);
    }

    @Test void optOutClaimAndInviteStatesCannotBeOverwritten() {
        for (var status : List.of(VenueDiscoveryCandidateStatus.OPTED_OUT, VenueDiscoveryCandidateStatus.CLAIMED,
                VenueDiscoveryCandidateStatus.INVITED, VenueDiscoveryCandidateStatus.DUPLICATE)) {
            when(candidates.findByIdForUpdate(2L)).thenReturn(Optional.of(candidate(status)));
            assertThrows(ResponseStatusException.class, () -> store.review(2L,
                    new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.DISCOVERED, status), actor));
        }
        verifyNoInteractions(audit);
    }

    @Test void reviewCannotCreateClaimsOrInvites() {
        var candidate = candidate(VenueDiscoveryCandidateStatus.DISCOVERED);
        when(candidates.findByIdForUpdate(2L)).thenReturn(Optional.of(candidate));
        assertThrows(ResponseStatusException.class, () -> store.review(2L,
                new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.CLAIMED,
                        VenueDiscoveryCandidateStatus.DISCOVERED), actor));
        verifyNoInteractions(audit);
    }

    @Test void shortlistIsAuditedAndIdempotent() {
        var candidate = candidate(VenueDiscoveryCandidateStatus.DISCOVERED);
        when(candidates.findByIdForUpdate(2L)).thenReturn(Optional.of(candidate));
        var result = store.review(2L, new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.SHORTLISTED,
                VenueDiscoveryCandidateStatus.DISCOVERED), actor);
        assertEquals("SHORTLISTED", result.status());
        verify(audit).record(argThat(command -> command.actorUserId().equals(1L)
                && command.newValues().get("status").equals("SHORTLISTED")));
        store.review(2L, new VenueDiscoveryRequest.Review(VenueDiscoveryCandidateStatus.SHORTLISTED,
                VenueDiscoveryCandidateStatus.SHORTLISTED), actor);
        verify(audit, times(1)).record(any());
    }

    @Test void repeatSearchDoesNotResetReviewOrDuplicateCandidates() {
        var run = new VenueDiscoveryRun();
        run.setId(10L);
        run.setStatus(VenueDiscoveryRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        when(runs.findById(10L)).thenReturn(Optional.of(run));
        var existing = candidate(VenueDiscoveryCandidateStatus.REJECTED);
        when(candidates.findBySourceAndSourcePlaceId(VenueDiscoverySource.GOOGLE_PLACES, "place_2"))
                .thenReturn(Optional.of(existing));
        var result = store.complete(10L, List.of("place_2", "place_2"), actor);
        assertEquals(1, result.candidates().size());
        assertEquals("REJECTED", result.candidates().getFirst().status());
        verify(observations, times(1)).save(any());
        verify(candidates, never()).save(any());
        verifyNoInteractions(audit);
        assertEquals(VenueDiscoveryRunStatus.COMPLETED, run.getStatus());
    }

    @Test void failureMessageNeverContainsExternalContent() {
        var run = new VenueDiscoveryRun();
        run.setStatus(VenueDiscoveryRunStatus.RUNNING);
        when(runs.findById(1L)).thenReturn(Optional.of(run));
        store.fail(1L);
        assertEquals(VenueDiscoveryRunStatus.FAILED, run.getStatus());
        assertEquals("Search failed. Check configuration or try again within the request limit.", run.getFailureReason());
    }
}
