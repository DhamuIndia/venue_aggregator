package com.staminal.venue.enquiries;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.discovery.VenueDiscoveryAccess;
import com.staminal.venue.enquiries.dto.EnquiryResponse;
import com.staminal.venue.enquiries.dto.UpdateApplicationVenueEnquiryRequest;
import com.staminal.venue.enums.EnquiryStatus;

@ExtendWith(MockitoExtension.class)
class ApplicationVenueEnquiryServiceTest {
    @Mock VenueDiscoveryAccess access;
    @Mock EnquiryRepository enquiries;
    @Mock EnquiryService mapper;
    @Mock AuditService audit;
    @Mock ApplicationVenueEnquiryNotifications notifications;
    ApplicationVenueEnquiryService service;
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("8", null, List.of());

    @BeforeEach void setup() { service = new ApplicationVenueEnquiryService(access, enquiries, mapper, audit, notifications); }

    @Test void queueRechecksDatabaseAdminBeforeReadingAnyContacts() {
        when(access.requireAdmin(auth)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Active admin required"));
        assertThatThrownBy(() -> service.list(0, 20, null, auth)).hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(enquiries, mapper, audit, notifications);
    }

    @Test void writesRecheckDatabaseAdminBeforeReadingAnyContacts() {
        when(access.requireAdmin(auth)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Active admin required"));
        assertThatThrownBy(() -> service.update("55", update(0, EnquiryStatus.CONTACTED), auth)).hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(enquiries, mapper, audit, notifications);
    }

    @Test void queueIsPagedAndOnlyIncludesTeamRoute() {
        when(enquiries.findByRoutingTargetAndStatus(eq(EnquiryRoutingTarget.VENUEMART), eq(EnquiryStatus.NEW), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(enquiry(EnquiryStatus.NEW)), invocation.getArgument(2), 41));
        var result = service.list(1, 20, EnquiryStatus.NEW, auth);
        assertThat(result.page()).isEqualTo(1); assertThat(result.totalItems()).isEqualTo(41);
        assertThat(result.totalPages()).isEqualTo(3); assertThat(result.items()).hasSize(1);
        verify(access).requireAdmin(auth);
    }

    @Test void invalidPaginationAndOwnerStatusCannotQueryQueue() {
        for (int[] paging : List.of(new int[]{-1,20}, new int[]{0,0}, new int[]{0,101}))
            assertThatThrownBy(() -> service.list(paging[0], paging[1], null, auth)).hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> service.list(0, 20, EnquiryStatus.CONFIRMED, auth)).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(enquiries, mapper);
    }

    @Test void ownerEnquiryCannotBeUpdatedInTeamQueue() {
        actor();
        when(enquiries.findForTeamUpdate(55L, EnquiryRoutingTarget.VENUEMART)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("55", update(0, EnquiryStatus.CONTACTED), auth)).hasMessageContaining("404 NOT_FOUND");
        verifyNoInteractions(mapper, audit, notifications);
    }

    @Test void staleVersionPreservesStatusResponseAndReason() {
        actor(); Enquiry enquiry = enquiry(EnquiryStatus.NEW); enquiry.setVersion(2L);
        when(enquiries.findForTeamUpdate(55L, EnquiryRoutingTarget.VENUEMART)).thenReturn(Optional.of(enquiry));
        assertThatThrownBy(() -> service.update("55", update(0, EnquiryStatus.CONTACTED), auth)).hasMessageContaining("409 CONFLICT");
        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.NEW); assertThat(enquiry.getTeamResponseMessage()).isNull();
        verify(enquiries, never()).saveAndFlush(any()); verifyNoInteractions(audit, notifications);
    }

    @Test void contactAndCloseNeverUseOwnerBookingSynchronizationAndAuditHasNoPii() {
        actor(); Enquiry enquiry = enquiry(EnquiryStatus.NEW);
        when(enquiries.findForTeamUpdate(55L, EnquiryRoutingTarget.VENUEMART)).thenReturn(Optional.of(enquiry));
        when(enquiries.saveAndFlush(enquiry)).thenAnswer(invocation -> { enquiry.setVersion(enquiry.getVersion()+1); return enquiry; });
        service.update("ENQ-000055", update(0, EnquiryStatus.CONTACTED), auth);
        assertThat(enquiry.getTeamUpdatedBy()).isEqualTo(18L); assertThat(enquiry.getRespondedAt()).isNotNull();
        assertThat(enquiry.getTeamResponseMessage()).isEqualTo("We are checking the dates.");
        assertThat(enquiry.getLastTeamUpdateReason()).isEqualTo("Contact attempt completed");
        assertThat(enquiry.getOwnerResponseMessage()).isNull();
        service.update("55", update(1, EnquiryStatus.CLOSED), auth);
        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.CLOSED); assertThat(enquiry.getVersion()).isEqualTo(2);
        ArgumentCaptor<AuditCommand> recorded = ArgumentCaptor.forClass(AuditCommand.class);
        verify(audit, times(2)).record(recorded.capture());
        assertThat(recorded.getAllValues().toString()).doesNotContain("Customer Phone", "Customer Email", "We are checking", "Contact attempt completed");
        assertThat(recorded.getValue().metadata()).containsEntry("reasonProvided", true);
        verify(mapper, times(2)).toResponse(enquiry);
        verify(notifications, times(2)).updated(enquiry);
        // Mapper is only used for presentation; owner-update/bookings methods are never called.
        verify(mapper, never()).updateOwnerEnquiryStatus(any(), any(), any());
    }

    @Test void sameStatusSkippingAndReopeningAreRejected() {
        actor(); Enquiry enquiry = enquiry(EnquiryStatus.NEW);
        when(enquiries.findForTeamUpdate(55L, EnquiryRoutingTarget.VENUEMART)).thenReturn(Optional.of(enquiry));
        assertThatThrownBy(() -> service.update("55", update(0, EnquiryStatus.CLOSED), auth)).hasMessageContaining("409 CONFLICT");
        enquiry.setStatus(EnquiryStatus.CONTACTED);
        assertThatThrownBy(() -> service.update("55", update(0, EnquiryStatus.CONTACTED), auth)).hasMessageContaining("409 CONFLICT");
        enquiry.setStatus(EnquiryStatus.CLOSED);
        assertThatThrownBy(() -> service.update("55", update(0, EnquiryStatus.CONTACTED), auth)).hasMessageContaining("409 CONFLICT");
        verify(enquiries, never()).saveAndFlush(any()); verifyNoInteractions(audit, notifications);
    }

    @ParameterizedTest @EnumSource(value=EnquiryStatus.class, names={"NEW", "PENDING_OWNER_RESPONSE", "CONFIRMED", "DECLINED", "COMPLETED"})
    void ownerWorkflowStatusesAreNotAccepted(EnquiryStatus next) {
        actor();
        assertThatThrownBy(() -> service.update("55", update(0, next), auth)).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(enquiries, mapper, audit, notifications);
    }

    private void actor() {
        Admin admin = new Admin(); admin.setId(18L);
        when(access.requireAdmin(auth)).thenReturn(new VenueDiscoveryAccess.Actor(8L, "ADMIN", admin));
    }
    private static UpdateApplicationVenueEnquiryRequest update(long version, EnquiryStatus status) {
        return new UpdateApplicationVenueEnquiryRequest(version, status, " We are checking the dates. ", " Contact attempt completed ");
    }
    private static Enquiry enquiry(EnquiryStatus status) {
        Enquiry result = new Enquiry(); result.setId(55L); result.setVersion(0L); result.setStatus(status);
        result.setRoutingTarget(EnquiryRoutingTarget.VENUEMART); result.setPublicationVersion(2L);
        result.setCustomerPhone("Customer Phone"); result.setCustomerEmail("Customer Email");
        return result;
    }
}
