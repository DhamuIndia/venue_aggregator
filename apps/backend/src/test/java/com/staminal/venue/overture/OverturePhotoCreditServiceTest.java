package com.staminal.venue.overture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.overture.OverturePhotoCreditResponse.Status;

class OverturePhotoCreditServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final VenueDiscoveryAccess access = mock(VenueDiscoveryAccess.class);
    final OvertureOnboardingProperties onboarding = mock(OvertureOnboardingProperties.class);
    final OvertureDraftMediaProperties media = mock(OvertureDraftMediaProperties.class);
    final OvertureDraftMediaService galleries = mock(OvertureDraftMediaService.class);
    final AuditService audit = mock(AuditService.class);
    final OverturePhotoCreditService service = new OverturePhotoCreditService(jdbc,access,onboarding,media,galleries,audit);
    final Authentication auth = new UsernamePasswordAuthenticationToken("7",null,List.of());
    final List<Object[]> inserts = new ArrayList<>();
    @BeforeEach void actor() {
        when(onboarding.isEnabled()).thenReturn(true); when(media.isEnabled()).thenReturn(true);
        Admin admin = new Admin(); admin.setId(4L); admin.setFullName("Active reviewer");
        when(access.requireAdmin(auth)).thenReturn(new VenueDiscoveryAccess.Actor(7L,"ADMIN",admin));
    }
    @Test void activeDatabaseAdminAndBothFlagsAreRequiredBeforeReadingMetadata() {
        when(access.requireAdmin(auth)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.save(55,1,save(0,0),auth)).hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(jdbc,galleries,audit);
    }
    @Test void disabledFlagsDenySaveAndReviewWithoutQueries() {
        when(media.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.save(55,1,save(0,0),auth)).hasMessageContaining("503 SERVICE_UNAVAILABLE");
        when(media.isEnabled()).thenReturn(true); when(onboarding.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.review(55,1,review(0,0),auth)).hasMessageContaining("503 SERVICE_UNAVAILABLE");
        verifyNoInteractions(jdbc,galleries,audit);
    }
    @Test void ownerOrPublishedHallCannotBeChanged() {
        assertThatThrownBy(() -> service.save(55,1,save(0,0),auth)).hasMessageContaining("404 NOT_FOUND");
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), eq(55L));
        assertThat(sql.getValue()).contains("h.listing_origin='APPLICATION'", "h.status='DRAFT'", "h.owner_user_id is null", "for update of h");
        verifyNoInteractions(galleries,audit);
    }
    @Test void staleGlobalVersionPreventsCreditAppend() throws Exception {
        state(3,"LICENSED_IMAGE","APPROVED","CC BY 4.0",null);
        assertThatThrownBy(() -> service.save(55,1,save(2,0),auth)).hasMessageContaining("409 CONFLICT");
        assertThat(inserts).isEmpty(); verifyNoInteractions(galleries,audit);
    }
    @Test void staleCreditVersionAndRevisionCapCannotAppend() throws Exception {
        state(3,"LICENSED_IMAGE","APPROVED","CC BY 4.0",credit(2,Status.PENDING));
        assertThatThrownBy(() -> service.save(55,1,save(3,1),auth)).hasMessageContaining("409 CONFLICT");
        state(3,"LICENSED_IMAGE","APPROVED","CC BY 4.0",credit(100,Status.PENDING));
        assertThatThrownBy(() -> service.save(55,1,save(3,100),auth)).hasMessageContaining("revision limit");
        assertThat(inserts).isEmpty(); verifyNoInteractions(galleries,audit);
    }
    @Test void nonLicensedAndArchivedPhotosAreNeverCredited() throws Exception {
        state(3,"TEAM_PHOTO","APPROVED",null,null);
        assertThatThrownBy(() -> service.save(55,1,save(3,0),auth)).hasMessageContaining("409 CONFLICT");
        state(3,"LICENSED_IMAGE","ARCHIVED","CC BY 4.0",null);
        assertThatThrownBy(() -> service.save(55,1,save(3,0),auth)).hasMessageContaining("404 NOT_FOUND");
        assertThat(inserts).isEmpty(); verifyNoInteractions(galleries,audit);
    }
    @Test void finalRevisionSlotIsReservedForReviewNotAnUnreviewableCorrection() throws Exception {
        state(3,"LICENSED_IMAGE","APPROVED","CC BY 4.0",credit(99,Status.PENDING));
        assertThatThrownBy(() -> service.save(55,1,save(3,99),auth)).hasMessageContaining("final credit revision slot is reserved");
        assertThat(inserts).isEmpty();
        service.review(55,1,review(3,99),auth);
        assertThat(inserts.getFirst()[2]).isEqualTo(100L); assertThat(inserts.getFirst()[10]).isEqualTo("APPROVED");
    }
    @Test void savingCorrectionAppendsPendingAndDoesNotReuseAnOlderApproval() throws Exception {
        state(3,"LICENSED_IMAGE","APPROVED","CC BY 4.0",credit(2,Status.APPROVED));
        service.save(55,1,save(3,2),auth);
        Object[] added=inserts.getFirst(); assertThat(added[2]).isEqualTo(3L); assertThat(added[10]).isEqualTo("PENDING");
        assertThat(added[14]).isNull(); assertThat(added[17]).isNull(); assertThat(added[18]).isEqualTo(false); assertThat(added[19]).isEqualTo(false);
        verify(galleries).gallery(55,auth);
        var recorded=ArgumentCaptor.forClass(AuditCommand.class); verify(audit).record(recorded.capture());
        assertThat(recorded.getValue().toString()).doesNotContain("Example photographer", "https://", "Original copyright", "No creative edits");
    }
    @Test void approvingCreditsCopiesImmutableMetadataAndAppendsIndependentReview() throws Exception {
        state(3,"LICENSED_IMAGE","APPROVED"," cc  by 4.0 ",credit(1,Status.PENDING));
        service.review(55,1,review(3,1),auth);
        Object[] added=inserts.getFirst(); assertThat(added[2]).isEqualTo(2L); assertThat(added[3]).isEqualTo("Original title");
        assertThat(added[10]).isEqualTo("APPROVED"); assertThat(added[14]).isEqualTo(4L); assertThat(added[17]).isEqualTo("Independently reviewed public credit evidence");
        assertThat(added[18]).isEqualTo(true); assertThat(added[19]).isEqualTo(true); verify(galleries).gallery(55,auth);
    }
    @Test void photoApprovalLicenseMatchAndCurrentPendingCreditAreRequired() throws Exception {
        state(3,"LICENSED_IMAGE","PENDING","CC BY 4.0",credit(1,Status.PENDING));
        assertThatThrownBy(() -> service.review(55,1,review(3,1),auth)).hasMessageContaining("Approve the photo");
        state(3,"LICENSED_IMAGE","APPROVED","CC BY-SA 4.0",credit(1,Status.PENDING));
        assertThatThrownBy(() -> service.review(55,1,review(3,1),auth)).hasMessageContaining("match supported immutable");
        state(3,"LICENSED_IMAGE","APPROVED","CC BY 4.0",credit(2,Status.APPROVED));
        assertThatThrownBy(() -> service.review(55,1,review(3,2),auth)).hasMessageContaining("Only the latest pending");
        assertThat(inserts).isEmpty(); verifyNoInteractions(galleries,audit);
    }
    @Test void approvalRequiresBothAttestationsEvenForDirectServiceCall() {
        for (var request : List.of(new OverturePhotoCreditRequest.Review(0,1,Status.APPROVED,"Reason long enough",false,true),
                new OverturePhotoCreditRequest.Review(0,1,Status.APPROVED,"Reason long enough",true,false)))
            assertThatThrownBy(() -> service.review(55,1,request,auth)).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(jdbc,galleries,audit);
    }
    @Test void rejectionAppendsCopyButDoesNotRequireApprovalOrSupportedLicense() throws Exception {
        state(3,"LICENSED_IMAGE","REJECTED","CC BY-SA 4.0",credit(1,Status.PENDING));
        service.review(55,1,new OverturePhotoCreditRequest.Review(3,1,Status.REJECTED,"License evidence is not supported",false,false),auth);
        assertThat(inserts.getFirst()[10]).isEqualTo("REJECTED"); assertThat(inserts.getFirst()[18]).isEqualTo(false);
    }
    private static OverturePhotoCreditRequest.Save save(long mediaVersion,long creditVersion) {
        var source=OverturePhotoCreditPolicyTest.save(); return new OverturePhotoCreditRequest.Save(mediaVersion,creditVersion,
                source.title(),source.creator(),source.creatorUrl(),source.sourceUrl(),source.licenseCode(),source.changesNotice(),source.requiredNotices());
    }
    private static OverturePhotoCreditRequest.Review review(long mediaVersion,long creditVersion) {
        return new OverturePhotoCreditRequest.Review(mediaVersion,creditVersion,Status.APPROVED,"Independently reviewed public credit evidence",true,true);
    }
    private static ResultSet credit(long version,Status status) throws Exception {
        ResultSet row=mock(ResultSet.class); when(row.getLong("credit_version")).thenReturn(version); when(row.getString("review_status")).thenReturn(status.name());
        when(row.getString("title")).thenReturn("Original title"); when(row.getString("creator")).thenReturn("Original creator");
        when(row.getString("source_url")).thenReturn("https://commons.wikimedia.org/wiki/File:Original.jpg");
        when(row.getString("license_code")).thenReturn("CC_BY_4_0"); when(row.getString("changes_notice")).thenReturn("No original changes");
        return row;
    }
    private void state(long version,String sourceKind,String status,String license,ResultSet credit) throws Exception {
        ResultSet hall=mock(ResultSet.class); when(hall.getLong(1)).thenReturn(55L);
        ResultSet photo=mock(ResultSet.class); when(photo.getString("source_kind")).thenReturn(sourceKind);
        when(photo.getString("status")).thenReturn(status); when(photo.getString("license_name")).thenReturn(license);
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<?>>any(),any(Object[].class))).thenAnswer(invocation -> {
            String sql=invocation.getArgument(0); RowMapper<?> mapper=invocation.getArgument(1);
            ResultSet row=sql.contains("select h.id")?hall:sql.contains("select source_kind")?photo:credit;
            return row==null?List.of():List.of(mapper.mapRow(row,0));
        });
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(55L))).thenReturn(version);
        when(jdbc.update(startsWith("update venue_overture_media_state set media_version"),eq(55L),eq(version))).thenReturn(1);
        doAnswer(invocation -> { inserts.add(((Object[]) invocation.getRawArguments()[1]).clone()); return 1; })
                .when(jdbc).update(startsWith("insert into venue_overture_photo_credits"),any(Object[].class));
    }
}
