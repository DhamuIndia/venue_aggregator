package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.discovery.VenueDiscoveryAccess.Actor;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.overture.OvertureDraftReviewResponse.ReviewStatus;
import com.staminal.venue.overture.OvertureDraftReviewResponse.DuplicateDecision;

class OvertureDraftReviewServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HallRepository halls = mock(HallRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final VenueDiscoveryAccess access = mock(VenueDiscoveryAccess.class);
    private final OvertureOnboardingProperties properties = mock(OvertureOnboardingProperties.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OvertureDraftReviewService service = new OvertureDraftReviewService(jdbc, halls, mapper, audit, access, properties);
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("7", null, List.of());

    @BeforeEach void setup() {
        when(properties.isEnabled()).thenReturn(true);
        Admin admin = new Admin(); admin.setId(4L); admin.setFullName("Active Admin");
        when(access.requireAdmin(authentication)).thenReturn(new Actor(7L, "ADMIN", admin));
    }
    @Test void disabledReviewRequiresAdminThenStopsBeforeAnyDataReadOrMutation() {
        when(properties.isEnabled()).thenReturn(false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service.detail(55, authentication)).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service.update(55, OvertureDraftReviewTestFixtures.update(), authentication)).getStatusCode());
        verify(access, times(2)).requireAdmin(authentication);
        verifyNoInteractions(jdbc, halls, audit);
    }
    @Test void inactiveOrUnauthorizedActorCannotReview() {
        when(access.requireAdmin(authentication)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.detail(55, authentication)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.update(55, OvertureDraftReviewTestFixtures.update(), authentication)).getStatusCode());
        verifyNoInteractions(jdbc, halls, audit);
    }
    @Test void missingReviewRowOrOwnerHallIsNotFoundWithoutFallback() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.detail(55, authentication)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.update(55, OvertureDraftReviewTestFixtures.update(), authentication)).getStatusCode());
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(55L));
        for (String statement : sql.getAllValues()) {
            assertTrue(statement.contains("h.listing_origin='APPLICATION'"));
            assertTrue(statement.contains("h.status='DRAFT'"));
            assertTrue(statement.contains("h.owner_user_id is null and h.owner_name is null"));
            assertTrue(statement.contains("join venue_overture_imports"));
        }
        verifyNoInteractions(halls, audit);
    }
    @Test void staleVersionStopsBeforeHallReadAndWrites() throws Exception {
        ResultSet row = row(2);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(55L)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1)).mapRow(row, 0)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.update(55, OvertureDraftReviewTestFixtures.update(), authentication)).getStatusCode());
        verifyNoInteractions(halls, audit);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void invalidIdentityDoesNotQueryStorage() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.detail(0, authentication)).getStatusCode());
        verifyNoInteractions(jdbc, halls, audit);
    }

    @Test void newlyAppearingDuplicateDemotesVerifiedDetailWithoutChangingHistory() throws Exception {
        ResultSet state = row(8);
        when(state.getString("review_status")).thenReturn("VERIFIED");
        mockDetail(state, true);
        var detail = service.detail(55, authentication);
        assertEquals(ReviewStatus.IN_REVIEW, detail.reviewStatus());
        assertEquals(DuplicateDecision.NOT_REVIEWED, detail.duplicateDecision());
        assertEquals(List.of(90L), detail.duplicates().stream().map(item -> item.hallId()).toList());
        assertEquals(8, detail.reviewVersion());
        assertEquals(List.of(), detail.reviewedDuplicateHallIds());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(halls, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }

    @Test void previouslyAcknowledgedMatchSetBecomingStaleIsUnresolvedWithoutAWrite() throws Exception {
        ResultSet state = row(9);
        when(state.getString("review_status")).thenReturn("VERIFIED");
        when(state.getString("duplicate_decision")).thenReturn("DISTINCT");
        when(state.getString("duplicate_notes")).thenReturn("Old assessment");
        when(state.getString("reviewed_duplicate_hall_ids")).thenReturn("[88]");
        mockDetail(state, true);
        var detail = service.detail(55, authentication);
        assertEquals(ReviewStatus.IN_REVIEW, detail.reviewStatus());
        assertEquals(DuplicateDecision.NOT_REVIEWED, detail.duplicateDecision());
        assertNull(detail.duplicateNotes()); assertTrue(detail.reviewedDuplicateHallIds().isEmpty());
        assertEquals(9, detail.reviewVersion());
        verify(jdbc, never()).update(anyString(), any(Object[].class)); verifyNoInteractions(audit);
    }

    @Test void verifiedDetailWithoutMatchesKeepsItsHistoricalVerifiedState() throws Exception {
        ResultSet state = row(10);
        when(state.getString("review_status")).thenReturn("VERIFIED");
        mockDetail(state, false);
        var detail = service.detail(55, authentication);
        assertEquals(ReviewStatus.VERIFIED, detail.reviewStatus());
        assertEquals(10, detail.reviewVersion());
        assertTrue(detail.duplicates().isEmpty());
    }

    private void mockDetail(ResultSet state, boolean withDuplicate) throws Exception {
        Halls hall = OvertureOnboardingStore.draft(OvertureFixtures.snapshot().venues().getFirst());
        hall.setId(55);
        when(halls.findById(55L)).thenReturn(java.util.Optional.of(hall));
        ResultSet match = mock(ResultSet.class);
        when(match.getLong("id")).thenReturn(90L);
        when(match.getString("name")).thenReturn("Nearby other venue");
        when(match.getString("city")).thenReturn("Chennai");
        when(match.getString("status")).thenReturn("APPROVED");
        when(match.getString("listing_origin")).thenReturn("OWNER");
        when(match.getObject("latitude", Double.class)).thenReturn(13.0001);
        when(match.getObject("longitude", Double.class)).thenReturn(80.2001);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper<?> rowMapper = invocation.getArgument(1);
                    if (sql.contains("select r.*")) return List.of(rowMapper.mapRow(state, 0));
                    return withDuplicate ? List.of(rowMapper.mapRow(match, 0)) : List.of();
                });
    }
    private ResultSet row(long version) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("source_id")).thenReturn(OvertureFixtures.ID);
        when(row.getLong("review_version")).thenReturn(version);
        when(row.getString("review_status")).thenReturn("UNREVIEWED");
        when(row.getString("source_facts")).thenReturn(mapper.writeValueAsString(OvertureDraftReviewTestFixtures.facts()));
        when(row.getString("verifications")).thenReturn("{}");
        when(row.getString("sources")).thenReturn("[]");
        when(row.getString("release")).thenReturn("2026-09-23.1");
        when(row.getTimestamp("imported_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-05T06:00:00Z")));
        when(row.getString("duplicate_decision")).thenReturn("NOT_REVIEWED");
        when(row.getString("reviewed_duplicate_hall_ids")).thenReturn("[]");
        return row;
    }
}
