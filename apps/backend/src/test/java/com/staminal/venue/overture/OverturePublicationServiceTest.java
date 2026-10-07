package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.HallListingOrigin;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.overture.OvertureDraftReviewResponse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class OverturePublicationServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final OvertureDraftReviewService reviews=mock(OvertureDraftReviewService.class);
    private final OvertureDraftMediaStorage storage=mock(OvertureDraftMediaStorage.class);
    private final VenueDiscoveryAccess access=mock(VenueDiscoveryAccess.class);
    private final AuditService audit=mock(AuditService.class);
    private final OvertureOnboardingProperties onboarding=mock(OvertureOnboardingProperties.class);
    private final OvertureDraftMediaProperties media=mock(OvertureDraftMediaProperties.class);
    private final OverturePublicationProperties properties=mock(OverturePublicationProperties.class);
    private final OverturePublicationService service=new OverturePublicationService(jdbc,new ObjectMapper().findAndRegisterModules(),reviews,storage,access,audit,onboarding,media,properties);
    private final Authentication auth=new UsernamePasswordAuthenticationToken("7",null,List.of());
    private boolean published;
    private long publicationVersion;
    private Long cover=1L;
    private List<Long> approved=List.of(1L);
    private boolean needsPublicCredit;
    private boolean creditReady;
    private long creditVersion=2;
    private byte[] bytes=new byte[]{1,2,3};
    @BeforeEach void setup() {
        when(onboarding.isEnabled()).thenReturn(true); when(media.isEnabled()).thenReturn(true); when(properties.isEnabled()).thenReturn(true);
        Admin admin=new Admin(); admin.setId(4); admin.setFullName("VenueMart Admin");
        when(access.requireAdmin(auth)).thenReturn(new VenueDiscoveryAccess.Actor(7L,"ADMIN",admin));
    }
    @Test void ownerPublicBehaviorDoesNotDependOnAnyApplicationFlagsOrQueries() {
        Halls owner=new Halls(); owner.setStatus(HallStatus.APPROVED);
        when(properties.isEnabled()).thenReturn(false);
        assertTrue(service.isPublic(owner)); owner.setStatus(HallStatus.DRAFT); assertFalse(service.isPublic(owner)); assertFalse(service.isPublic(null));
        verifyNoInteractions(jdbc,storage,reviews);
    }
    @Test void disabledFeaturesAndUnexpectedOwnershipFailClosedBeforeReadingStorage() {
        Halls app=application(); when(properties.isEnabled()).thenReturn(false);
        assertFalse(service.isPublic(app)); assertEquals(HttpStatus.NOT_FOUND,failure(()->service.publicPhoto(55,1,1)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,failure(()->service.publish(55,publish(),auth)));
        when(properties.isEnabled()).thenReturn(true); app.setOwnerName("Unexpected owner"); assertFalse(service.isPublic(app));
        verifyNoInteractions(jdbc,storage,audit);
    }
    @Test void activeDatabaseAdminIsRequiredBeforeReadingPrivatePublicationMetadata() {
        when(access.requireAdmin(auth)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertEquals(HttpStatus.FORBIDDEN,failure(()->service.detail(55,auth)));
        assertEquals(HttpStatus.FORBIDDEN,failure(()->service.unpublish(55,new OverturePublicationRequest.Unpublish(0,"Withdraw for correction"),auth)));
        verifyNoInteractions(jdbc,storage,reviews,audit);
    }
    @Test void unownedDraftNeedsCurrentVerificationCoverAndPermittedPhotos() throws Exception {
        mockWorkflow(); when(reviews.publicationAssessment(55)).thenReturn(assessment(ReviewStatus.IN_REVIEW,DuplicateDecision.NOT_REVIEWED,List.of(),List.of()));
        cover=null; approved=List.of();
        var detail=service.detail(55,auth);
        assertFalse(detail.ready()); assertTrue(detail.blockers().stream().anyMatch(v->v.contains("factual")));
        assertTrue(detail.blockers().stream().anyMatch(v->v.contains("at least one"))); assertTrue(detail.blockers().stream().anyMatch(v->v.contains("cover")));
        assertEquals(HttpStatus.CONFLICT,failure(()->service.publish(55,publish(),auth))); verifyNoInteractions(storage,audit);
    }
    @Test void freshDuplicateSetMustBeCompletelyAcknowledgedAsDistinct() throws Exception {
        mockWorkflow(); var duplicate=new Duplicate(99,"Nearby Venue","Chennai","Adyar","APPROVED","OWNER",20.0);
        when(reviews.publicationAssessment(55)).thenReturn(assessment(ReviewStatus.VERIFIED,DuplicateDecision.DISTINCT,List.of(duplicate),List.of()));
        assertEquals(HttpStatus.CONFLICT,failure(()->service.publish(55,publish(),auth))); verifyNoInteractions(storage,audit);
    }
    @Test void licensedImageStaysPrivateWhileSeparateLicensedRolloutFlagIsOff() throws Exception {
        mockWorkflow(); needsPublicCredit=true;
        var detail=service.detail(55,auth); assertFalse(detail.ready());
        assertTrue(detail.blockers().stream().anyMatch(v->v.contains("public credit")));
        assertEquals(HttpStatus.CONFLICT,failure(()->service.publish(55,publish(),auth))); verifyNoInteractions(storage,audit);
    }
    @Test void missingPendingRejectedAndUnsupportedLatestCreditsFailClosed() throws Exception {
        mockWorkflow(); needsPublicCredit=true; when(properties.isLicensedPhotosEnabled()).thenReturn(true);
        for(long currentVersion:List.of(0L,1L,2L,100L)) {
            creditVersion=currentVersion; creditReady=false;
            var detail=service.detail(55,auth); assertFalse(detail.ready());
            assertTrue(detail.blockers().stream().anyMatch(value->value.contains("latest approved public credit")));
            assertEquals(HttpStatus.CONFLICT,failure(()->service.publish(55,publish(),auth)));
        }
        verifyNoInteractions(storage,audit);
    }
    @Test void licensedPublicationSnapshotsOnlyTheCurrentApprovedSafeCredit() throws Exception {
        mockWorkflow(); needsPublicCredit=true; creditReady=true; when(properties.isLicensedPhotosEnabled()).thenReturn(true);
        assertTrue(service.detail(55,auth).ready());
        var after=service.publish(55,publish(),auth); assertEquals(1,after.publicationVersion());
        verify(jdbc).update(contains("insert into venue_overture_publication_photo_credits"),eq(55L),eq(1L),eq(1L),eq(2L),
                eq(55L),eq(1L),eq(2L),eq(55L),eq(1L),eq(2L));
        verify(storage).ensurePrivate(); verify(audit).record(any());
    }
    @Test void licensedKillSwitchDeniesPublicReadsButNotWithdrawal() throws Exception {
        mockWorkflow(); needsPublicCredit=true; creditReady=true; published=true; publicationVersion=1;
        when(properties.isLicensedPhotosEnabled()).thenReturn(false);
        assertFalse(service.isPublic(application()));
        assertEquals(HttpStatus.NOT_FOUND,failure(()->service.publicListing(55)));
        assertEquals(HttpStatus.NOT_FOUND,failure(()->service.publicPhoto(55,1,1)));
        assertEquals(HttpStatus.NOT_FOUND,failure(()->service.lockPublishedForEnquiry(55)));
        var after=service.unpublish(55,new OverturePublicationRequest.Unpublish(1,"Withdraw licensed image immediately"),auth);
        assertEquals(OverturePublicationResponse.State.UNPUBLISHED,after.publicationState());
        verifyNoInteractions(storage);
    }
    @Test void allThreeVersionsAreCheckedBeforeAnyObjectDownloadOrPublicationWrite() throws Exception {
        mockWorkflow();
        for(var request:List.of(new OverturePublicationRequest.Publish(1,2,3,"Ready for publication"),
                new OverturePublicationRequest.Publish(0,1,3,"Ready for publication"),new OverturePublicationRequest.Publish(0,2,2,"Ready for publication")))
            assertEquals(HttpStatus.CONFLICT,failure(()->service.publish(55,request,auth)));
        verifyNoInteractions(storage,audit);
        verify(jdbc,never()).update(startsWith("delete from venue_overture_publication_photos"),anyLong());
    }
    @Test void readyPublicationReadsCanonicalPhotosAndWritesSnapshotAndAuditOnly() throws Exception {
        mockWorkflow(); var after=service.publish(55,publish(),auth);
        assertEquals(1,after.publicationVersion()); assertEquals(OverturePublicationResponse.State.PUBLISHED,after.publicationState());
        verify(storage).ensurePrivate(); verify(storage).read(anyString(),eq(3));
        verify(jdbc).update(contains("insert into venue_overture_publication_photos"),eq(55L),eq(1L),eq(1L),eq(0));
        verify(jdbc).update(contains("insert into venue_overture_publication_history"),any(Object[].class)); verify(audit).record(any());
        verify(jdbc,never()).update(contains("insert into venue_overture_publication_photo_credits"),any(Object[].class));
        verify(storage,never()).put(anyString(),any(),anyString()); verify(storage,never()).delete(anyString());
    }
    @Test void corruptBytesBlockPublishWithoutSnapshotHistoryOrAudit() throws Exception {
        mockWorkflow(); when(storage.read(anyString(),eq(3))).thenReturn(new byte[]{1,2,4});
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,failure(()->service.publish(55,publish(),auth)));
        verifyNoInteractions(audit); verify(jdbc,never()).update(contains("insert into venue_overture_publication_history"),any(Object[].class));
    }
    @Test void publishedVenueIsFrozenAndCannotBePublishedTwice() throws Exception {
        mockWorkflow(); published=true; publicationVersion=1;
        assertEquals(HttpStatus.CONFLICT,failure(()->service.publish(55,new OverturePublicationRequest.Publish(1,2,3,"Publish again prohibited"),auth)));
        verifyNoInteractions(storage,audit);
    }
    @Test void withdrawalRemainsAvailableWithKillSwitchOffAndNeverDeletesMedia() throws Exception {
        mockWorkflow(); published=true; publicationVersion=1; when(properties.isEnabled()).thenReturn(false);
        var after=service.unpublish(55,new OverturePublicationRequest.Unpublish(1,"Withdraw to correct facts"),auth);
        assertEquals(2,after.publicationVersion()); assertEquals(OverturePublicationResponse.State.UNPUBLISHED,after.publicationState());
        assertFalse(after.ready()); verifyNoInteractions(storage); verify(audit).record(any());
    }
    @Test void duplicateFloodDoesNotBlockWithdrawalButFailsClosedForFuturePublication() throws Exception {
        mockWorkflow(); published=true; publicationVersion=1;
        when(reviews.publicationAssessment(55)).thenAnswer(invocation->{
            Detail value=assessment(ReviewStatus.IN_REVIEW,DuplicateDecision.NOT_REVIEWED,List.of(),List.of());
            return new Detail(value.hallId(),value.sourceId(),value.status(),value.reviewVersion(),value.reviewStatus(),value.facts(),value.sourceFacts(),
                    value.sources(),value.release(),value.importedAt(),value.fieldOrigins(),value.verifications(),value.missingFields(),value.unverifiedFields(),
                    value.duplicates(),value.duplicateDecision(),value.duplicateNotes(),value.reviewedDuplicateHallIds(),value.reviewNotes(),value.lastReviewedBy(),value.lastReviewedAt(),true);
        });
        var before=service.detail(55,auth); assertFalse(before.ready()); assertTrue(before.blockers().stream().anyMatch(v->v.contains("Too many duplicate")));
        var after=service.unpublish(55,new OverturePublicationRequest.Unpublish(1,"Withdraw despite duplicate flood"),auth);
        assertEquals(OverturePublicationResponse.State.UNPUBLISHED,after.publicationState()); assertEquals(2,after.publicationVersion());
        assertFalse(after.ready()); assertTrue(after.blockers().stream().anyMatch(v->v.contains("Too many duplicate")));
        assertEquals(HttpStatus.CONFLICT,failure(()->service.publish(55,new OverturePublicationRequest.Publish(2,2,3,"Republish without assessment prohibited"),auth)));
        verifyNoInteractions(storage); verify(audit).record(any());
    }
    @Test void publicGateRequiresMatchingExplicitSnapshotNotJustApprovedStatus() {
        assertFalse(service.isPublic(application()));
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(),org.mockito.ArgumentMatchers.<RowMapper<?>>any(),eq(55L),isNull(),isNull(),eq(false));
        for(String invariant:List.of("publication_state='PUBLISHED'","published_review_version=r.review_version","published_media_version=m.media_version",
                "venue_overture_publication_history","dp.status='APPROVED'","count(*)","pp.sort_order","overture_publication_photo_credit_valid","not ?::boolean")) assertTrue(sql.getValue().contains(invariant));
        verifyNoInteractions(storage);
    }
    @Test void publicContentIsBoundedSelectedAndRevisionGatedWhileEnquiryGetsLockedRevision() throws Exception {
        mockWorkflow(); published=true; publicationVersion=1;
        assertArrayEquals(bytes,service.publicPhoto(55,1,1)); assertEquals(1,service.lockPublishedForEnquiry(55));
        assertEquals(HttpStatus.NOT_FOUND,failure(()->service.publicPhoto(55,1,0)));
        published=false; assertEquals(HttpStatus.NOT_FOUND,failure(()->service.publicPhoto(55,1,1)));
        verify(jdbc,times(2)).query(contains("for share"),org.mockito.ArgumentMatchers.<RowMapper<?>>any(),eq(55L));
    }
    @Test void publicPresentationReturnsApplicationPathsAndOnlyLicensedSourceMetadata() throws Exception {
        mockWorkflow(); published=true; publicationVersion=1;
        var value=service.publicListing(55);
        assertEquals(List.of("/api/v1/halls/55/application-photos/1?publicationVersion=1"),value.galleryUrls());
        assertEquals(1,value.applicationPhotos().size()); assertFalse(value.applicationPhotos().getFirst().requiresCredit());
        assertNull(value.applicationPhotos().getFirst().credit());
        String json=new ObjectMapper().writeValueAsString(value);
        for(String privateField:List.of("storage_key","drafts/55","permissionEvidence","reviewNotes","VenueMart Admin","verification evidence")) assertFalse(json.contains(privateField));
    }
    @Test void publicLicensedCreditsComeFromImmutableSnapshotAndDoNotExposePrivateHistory() throws Exception {
        mockWorkflow(); needsPublicCredit=true; creditReady=true; published=true; publicationVersion=1;
        when(properties.isLicensedPhotosEnabled()).thenReturn(true);
        var listing=service.publicListing(55); var photo=listing.applicationPhotos().getFirst();
        assertTrue(photo.requiresCredit()); assertEquals(1,photo.photoId()); assertEquals("Public venue image",photo.credit().title());
        assertEquals("CC BY 4.0",photo.credit().licenseLabel()); assertEquals("https://creativecommons.org/licenses/by/4.0/",photo.credit().licenseUrl());
        String json=new ObjectMapper().writeValueAsString(listing);
        for(String privateField:List.of("permissionEvidence","sourceReference","reviewReason","reviewedBy","VenueMart Admin","rightsConfirmed","creditVersion","storage_key"))
            assertFalse(json.contains(privateField));
        verify(jdbc).query(contains("pc.credit_snapshot::text"),org.mockito.ArgumentMatchers.<RowMapper<?>>any(),eq(55L),eq(1L),eq(1L));
    }
    private void mockWorkflow() throws Exception {
        when(reviews.publicationAssessment(55)).thenAnswer(i->assessment(ReviewStatus.VERIFIED,DuplicateDecision.NOT_REVIEWED,List.of(),List.of()));
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<?>>any(),any(Object[].class))).thenAnswer(invocation->{
            String sql=invocation.getArgument(0); RowMapper<?> rowMapper=invocation.getArgument(1); List<ResultSet> rows=new java.util.ArrayList<>();
            ResultSet row=mock(ResultSet.class);
            if(sql.startsWith("select h.id") || sql.startsWith("select id from halls") || sql.startsWith("select hall_id")) {when(row.getLong(1)).thenReturn(55L); rows.add(row);}
            else if(sql.startsWith("select publication_version")) {when(row.getLong("publication_version")).thenReturn(publicationVersion); when(row.getString("publication_state")).thenReturn(published?"PUBLISHED":"UNPUBLISHED"); rows.add(row);}
            else if(sql.startsWith("select media_version")) {when(row.getLong("media_version")).thenReturn(3L); when(row.getObject("cover_media_id",Long.class)).thenReturn(cover); rows.add(row);}
            else if(sql.startsWith("select id from venue_overture_draft_photos")) {for(long id:approved) {ResultSet photo=mock(ResultSet.class);when(photo.getLong(1)).thenReturn(id);rows.add(photo);}}
            else if(sql.stripLeading().startsWith("select dp.id") && needsPublicCredit) {
                when(row.getLong("id")).thenReturn(1L);when(row.getLong("credit_version")).thenReturn(creditVersion);
                when(row.getBoolean("credit_ready")).thenReturn(creditReady);rows.add(row);
            }
            else if(sql.startsWith("select storage_key")) {when(row.getString("storage_key")).thenReturn("drafts/55/00000000-0000-0000-0000-000000000001.jpg");when(row.getInt("size_bytes")).thenReturn(3);when(row.getString("sha256")).thenReturn(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));rows.add(row);}
            else if(sql.startsWith("select p.publication_version") && published && (!needsPublicCredit || properties.isLicensedPhotosEnabled() && creditReady)) {when(row.getLong("publication_version")).thenReturn(publicationVersion);when(row.getLong("cover_media_id")).thenReturn(1L);when(row.getString("sources")).thenReturn("[]");when(row.getString("release")).thenReturn("2026-09-23.0");when(row.getString("verifications")).thenReturn("{}");rows.add(row);}
            else if(sql.startsWith("select pp.photo_id") && published) {
                when(row.getLong("photo_id")).thenReturn(1L);when(row.getString("source_kind")).thenReturn(needsPublicCredit?"LICENSED_IMAGE":"TEAM_PHOTO");
                when(row.getString("credit_snapshot")).thenReturn("{\"title\":\"Public venue image\",\"creator\":\"Photographer\",\"creatorUrl\":null,\"sourceUrl\":\"https://images.example.com/photo/1\",\"licenseCode\":\"CC_BY_4_0\",\"licenseLabel\":\"CC BY 4.0\",\"licenseUrl\":\"https://creativecommons.org/licenses/by/4.0/\",\"changesNotice\":\"No earlier changes supplied\",\"processingNotice\":\"VenueMart normalized this image to JPEG and may have resized it.\",\"requiredNotices\":null}"); rows.add(row);
            }
            else if(sql.startsWith("select photo_id") && published) {when(row.getLong(1)).thenReturn(1L);rows.add(row);}
            List<Object> result=new java.util.ArrayList<>(); for(ResultSet item:rows) result.add(rowMapper.mapRow(item,result.size()));return result;
        });
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            if(sql.contains("update venue_overture_publications set")) {
                publicationVersion++;
                // Inspect SET, not the old-state predicate in WHERE during withdrawal.
                published=sql.contains("publication_state='PUBLISHED',");
            }
            return 1;
        });
        when(storage.read(anyString(),eq(3))).thenReturn(bytes);
    }
    private Detail assessment(ReviewStatus status,DuplicateDecision decision,List<Duplicate> duplicates,List<Long> reviewed) {
        Map<String,Verification> verifications=new java.util.LinkedHashMap<>();
        for(String field:OvertureDraftReviewPolicy.REQUIRED) verifications.put(field,new Verification(4,"VenueMart Admin",Instant.EPOCH,"private verification evidence"));
        return new Detail(55,"sample-source",published?"APPROVED":"DRAFT",2,status,OvertureDraftReviewTestFixtures.facts(),OvertureDraftReviewTestFixtures.facts(),
                List.of(new OvertureResponse.Source("OpenStreetMap","ODbL-1.0","fixture")),"2026-09-23.0",Instant.EPOCH,Map.of(),verifications,List.of(),List.of(),duplicates,decision,null,reviewed,null,null,null);
    }
    private Halls application() {Halls hall=new Halls();hall.setId(55);hall.setListingOrigin(HallListingOrigin.APPLICATION);hall.setStatus(HallStatus.APPROVED);return hall;}
    private OverturePublicationRequest.Publish publish() {return new OverturePublicationRequest.Publish(0,2,3,"Ready for controlled publication");}
    private HttpStatus failure(Runnable action) {return HttpStatus.valueOf(assertThrows(ResponseStatusException.class,action::run).getStatusCode().value());}
}
