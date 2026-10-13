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
import com.staminal.venue.admin.Admin;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;

class OvertureDraftMediaServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);
    private final VenueDiscoveryAccess access = mock(VenueDiscoveryAccess.class);
    private final OvertureOnboardingProperties onboarding = mock(OvertureOnboardingProperties.class);
    private final OvertureDraftMediaProperties media = mock(OvertureDraftMediaProperties.class);
    private final OvertureDraftImageProcessor images = mock(OvertureDraftImageProcessor.class);
    private final OvertureDraftMediaStorage storage = mock(OvertureDraftMediaStorage.class);
    private final OvertureDraftMediaService service = new OvertureDraftMediaService(jdbc,audit,access,onboarding,media,images,storage);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("7",null,List.of());
    @BeforeEach void setup() {
        when(onboarding.isEnabled()).thenReturn(true); when(media.isEnabled()).thenReturn(true);
        Admin admin = new Admin(); admin.setId(4); admin.setFullName("Active Admin");
        when(access.requireAdmin(auth)).thenReturn(new VenueDiscoveryAccess.Actor(7L,"ADMIN",admin));
    }
    @Test void bothFlagsAndActiveAdminAreRequiredBeforeReadingOrProcessing() {
        when(media.isEnabled()).thenReturn(false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure(() -> service.gallery(55,auth)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure(() -> service.upload(55,null,OvertureDraftMediaFixtures.upload(),auth)));
        when(media.isEnabled()).thenReturn(true); when(onboarding.isEnabled()).thenReturn(false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure(() -> service.content(55,1,auth)));
        when(access.requireAdmin(auth)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertEquals(HttpStatus.FORBIDDEN, failure(() -> service.review(55,1,new OvertureDraftMediaRequest.Review(0,PhotoStatus.APPROVED,"Checked usage rights"),auth)));
        verifyNoInteractions(jdbc,images,storage,audit);
    }
    @Test void ownerHallOrMissingFactualReviewIsNotFoundWithoutStorageCalls() {
        assertEquals(HttpStatus.NOT_FOUND, failure(() -> service.gallery(55,auth)));
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(55L));
        assertTrue(sql.getValue().contains("h.listing_origin='APPLICATION'"));
        assertTrue(sql.getValue().contains("h.status='DRAFT'"));
        assertTrue(sql.getValue().contains("h.owner_user_id is null and h.owner_name is null"));
        assertTrue(sql.getValue().contains("join venue_overture_draft_reviews"));
        verifyNoInteractions(images,storage,audit);
    }
    @Test void staleMediaVersionStopsBeforeImageOrStorageProcessing() throws Exception {
        mockState(3,List.of(),null);
        assertEquals(HttpStatus.CONFLICT, failure(() -> service.upload(55,null,OvertureDraftMediaFixtures.upload(),auth)));
        verifyNoInteractions(images,storage,audit);
    }
    @Test void activePhotoCountQuotaStopsBeforeProcessing() throws Exception {
        mockState(0,java.util.Collections.nCopies(20,photo("PENDING",1)),null);
        assertEquals(HttpStatus.CONFLICT, failure(() -> service.upload(55,null,OvertureDraftMediaFixtures.upload(),auth)));
        verifyNoInteractions(images,storage,audit);
    }
    @Test void archivedPhotosStillConsumeLifetimeAndRetainedQuotas() throws Exception {
        mockState(0,java.util.Collections.nCopies(100,photo("ARCHIVED",1)),null);
        assertEquals(HttpStatus.CONFLICT, failure(() -> service.upload(55,null,OvertureDraftMediaFixtures.upload(),auth)));
        mockState(0,java.util.Collections.nCopies(16,photo("ARCHIVED",4194304)),null);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure(() -> service.upload(55,null,OvertureDraftMediaFixtures.upload(),auth)));
        verifyNoInteractions(images,storage,audit);
    }
    @Test void archivedContentIsNotServedAndNoObjectIsFetched() throws Exception {
        mockState(0,List.of(),photo("ARCHIVED",3));
        assertEquals(HttpStatus.NOT_FOUND, failure(() -> service.content(55,1,auth)));
        verifyNoInteractions(storage);
    }
    @Test void contentSizeAndHashMustMatchImmutableStoredMetadata() throws Exception {
        mockState(0,List.of(),photo("PENDING",3));
        when(storage.read(anyString(),eq(3))).thenReturn(new byte[]{1,2,3});
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure(() -> service.content(55,1,auth)));
        when(storage.read(anyString(),eq(3))).thenReturn(new byte[]{1,2});
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure(() -> service.content(55,1,auth)));
    }
    @Test void reviewedPhotosCannotBeReviewedAgainAndArchivedPhotosCannotBeRestored() throws Exception {
        mockState(0,List.of(),photo("APPROVED",3));
        assertEquals(HttpStatus.CONFLICT, failure(() -> service.review(55,1,new OvertureDraftMediaRequest.Review(0,PhotoStatus.REJECTED,"Changing old review"),auth)));
        mockState(0,List.of(),photo("ARCHIVED",3));
        assertEquals(HttpStatus.CONFLICT, failure(() -> service.archive(55,1,new OvertureDraftMediaRequest.Archive(0,"Archive again prohibited"),auth)));
        verifyNoInteractions(storage,images,audit);
    }
    @Test void galleryContainsNoObjectKeysOrImageUrlsAndRetainsArchiveMetadata() throws Exception {
        mockState(0,List.of(photo("ARCHIVED",3)),null);
        var gallery = service.gallery(55,auth);
        assertEquals(0,gallery.activeCount()); assertEquals(3,gallery.retainedBytes());
        assertEquals(1,gallery.items().size());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(gallery);
        assertFalse(json.contains("storageKey")); assertFalse(json.contains("drafts/55/")); assertFalse(json.contains("contentUrl"));
        assertEquals(100,gallery.limits().maxLifetimePhotos());
    }
    @Test void galleryAlwaysProjectsLatestCreditRevisionEvenWhenItIsPending() throws Exception {
        ResultSet photo = photo("APPROVED",3);
        when(photo.getString("source_kind")).thenReturn("LICENSED_IMAGE");
        when(photo.getString("rights_basis")).thenReturn("OPEN_LICENSE");
        when(photo.getString("license_name")).thenReturn("CC BY 4.0");
        when(photo.getObject("credit_version",Long.class)).thenReturn(3L);
        when(photo.getString("credit_status")).thenReturn("PENDING");
        when(photo.getString("credit_license_code")).thenReturn("CC_BY_4_0");
        when(photo.getString("credit_title")).thenReturn("Corrected title");
        when(photo.getString("credit_creator")).thenReturn("Credited author");
        when(photo.getString("credit_source_url")).thenReturn("https://commons.wikimedia.org/wiki/File:Example.jpg");
        when(photo.getString("credit_changes_notice")).thenReturn("No creative edits");
        when(photo.getTimestamp("credit_changed_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-06T10:00:00Z")));
        mockState(3,List.of(photo),null);
        var gallery=service.gallery(55,auth);
        assertEquals(3,gallery.items().getFirst().credit().creditVersion());
        assertEquals(OverturePhotoCreditResponse.Status.PENDING,gallery.items().getFirst().credit().status());
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc,atLeastOnce()).query(sql.capture(),org.mockito.ArgumentMatchers.<RowMapper<?>>any(),any(Object[].class));
        assertTrue(sql.getAllValues().stream().anyMatch(query -> query.contains("left join lateral") && query.contains("order by credit_version desc limit 1")));
    }
    private HttpStatus failure(Runnable action) { return HttpStatus.valueOf(assertThrows(ResponseStatusException.class,action::run).getStatusCode().value()); }
    private void mockState(long version,List<ResultSet> galleryRows,ResultSet selected) throws Exception {
        ResultSet state = mock(ResultSet.class); when(state.getLong("media_version")).thenReturn(version);
        ResultSet hall = mock(ResultSet.class); when(hall.getLong(1)).thenReturn(55L);
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<?>>any(),any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0); RowMapper<?> mapper = invocation.getArgument(1);
            if (sql.contains("select h.id")) return List.of(mapper.mapRow(hall,0));
            if (sql.contains("select media_version")) return List.of(mapper.mapRow(state,0));
            if (sql.contains("and id=?")) return selected == null ? List.of() : List.of(mapper.mapRow(selected,0));
            var rows = new java.util.ArrayList<Object>();
            for (ResultSet row : galleryRows) rows.add(mapper.mapRow(row,rows.size()));
            return rows;
        });
        when(jdbc.queryForObject(anyString(),org.mockito.ArgumentMatchers.<RowMapper<?>>any(),eq(55L)))
                .thenAnswer(invocation -> ((RowMapper<?>) invocation.getArgument(1)).mapRow(state,0));
    }
    private ResultSet photo(String status,int size) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(1L); when(row.getLong("hall_id")).thenReturn(55L);
        when(row.getString("status")).thenReturn(status); when(row.getString("source_kind")).thenReturn("TEAM_PHOTO");
        when(row.getString("rights_basis")).thenReturn("TEAM_OWNED"); when(row.getString("permission_evidence")).thenReturn("Documented team ownership");
        when(row.getBoolean("rights_confirmed")).thenReturn(true); when(row.getLong("uploaded_by")).thenReturn(4L);
        when(row.getString("uploader_name")).thenReturn("Active Admin"); when(row.getTimestamp("uploaded_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-05T06:00:00Z")));
        when(row.getInt("width")).thenReturn(2); when(row.getInt("height")).thenReturn(2); when(row.getInt("size_bytes")).thenReturn(size);
        when(row.getString("sha256")).thenReturn("0".repeat(64));
        when(row.getString("storage_key")).thenReturn("drafts/55/00000000-0000-0000-0000-000000000001.jpg");
        return row;
    }
}
