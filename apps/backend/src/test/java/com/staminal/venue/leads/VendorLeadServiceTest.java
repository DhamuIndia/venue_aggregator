package com.staminal.venue.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.enums.PreferredContactChannel;
import com.staminal.venue.leads.Dto.CreateVendorLeadRequest;
import com.staminal.venue.leads.Dto.UpdateVendorLeadStatusRequest;
import com.staminal.venue.leads.Dto.VendorLeadResponse;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendorbookings.VendorServiceBookingRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class VendorLeadServiceTest {

    @Mock
    private VendorLeadRepository vendorLeadRepository;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private VendorServiceBookingRepository vendorServiceBookingRepository;

    private VendorLeadService vendorLeadService;

    @BeforeEach
    void setUp() {
        vendorLeadService = new VendorLeadService(
                vendorLeadRepository,
                vendorRepository,
                userRepository,
                auditService,
                notificationService,
                vendorServiceBookingRepository);
    }

    @Test
    void createLeadAcceptsApprovedVendorSlugAndReturnsVendorIdentity() {
        User customer = customer();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        CreateVendorLeadRequest request = createRequest("saffron-leaf-catering");

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(vendorRepository.findByStatus(VendorStatus.APPROVED)).thenReturn(List.of(vendor));
        when(vendorLeadRepository.save(any(VendorLead.class))).thenAnswer(invocation -> {
            VendorLead lead = invocation.getArgument(0);
            lead.setId(901L);
            return lead;
        });

        VendorLeadResponse response = vendorLeadService.createLead(request, customerAuth());

        ArgumentCaptor<VendorLead> leadCaptor = ArgumentCaptor.forClass(VendorLead.class);
        verify(vendorLeadRepository).save(leadCaptor.capture());

        VendorLead savedLead = leadCaptor.getValue();
        assertThat(savedLead.getVendor()).isSameAs(vendor);
        assertThat(savedLead.getCustomer()).isSameAs(customer);
        assertThat(savedLead.getStatus()).isEqualTo(VendorLeadStatus.NEW);

        assertThat(response.getId()).isEqualTo(901L);
        assertThat(response.getVendorId()).isEqualTo("501");
        assertThat(response.getVendorName()).isEqualTo("Saffron Leaf Catering");
        assertThat(response.getCustomerId()).isEqualTo("101");
        assertThat(response.getSource()).isEqualTo("DIRECT_ENQUIRY");
        assertThat(response.isContactDetailsShared()).isTrue();
        assertThat(response.getRequirementId()).isNull();
        assertThat(response.getStatus()).isEqualTo(VendorLeadStatus.NEW);
    }

    @Test
    void createLeadRejectsUnapprovedVendor() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(vendorRepository.findById(501L)).thenReturn(Optional.of(vendor(VendorStatus.PENDING)));

        assertThatThrownBy(() -> vendorLeadService.createLead(createRequest("501"), customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateStatusAllowsContractTransitionFromNewToQuoteSent() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);
        VendorLead lead = lead(vendor, VendorLeadStatus.NEW);
        UpdateVendorLeadStatusRequest request = new UpdateVendorLeadStatusRequest();
        request.setStatus(VendorLeadStatus.QUOTE_SENT);

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(901L, 501L)).thenReturn(Optional.of(lead));
        when(vendorLeadRepository.save(any(VendorLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorLeadResponse response = vendorLeadService.updateStatus(901L, request, vendorAuth());

        assertThat(response.getStatus()).isEqualTo(VendorLeadStatus.QUOTE_SENT);
        verify(auditService).record(any());
    }

    @Test
    void updateStatusAllowsVendorToShowInterest() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);
        VendorLead lead = lead(vendor, VendorLeadStatus.NEW);
        UpdateVendorLeadStatusRequest request = new UpdateVendorLeadStatusRequest();
        request.setStatus(VendorLeadStatus.INTERESTED);

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(901L, 501L)).thenReturn(Optional.of(lead));
        when(vendorLeadRepository.save(any(VendorLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorLeadResponse response = vendorLeadService.updateStatus(901L, request, vendorAuth());

        assertThat(response.getStatus()).isEqualTo(VendorLeadStatus.INTERESTED);
        verify(notificationService).notifyUser(
                eq(lead.getCustomer()),
                eq(NotificationType.ENQUIRY),
                eq("Vendor is interested"),
                eq("Saffron Leaf Catering is interested in your requirement."),
                eq("/customer?tab=enquiries"));
    }

    @Test
    void decliningLeadRequiresCustomerVisibleReason() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);
        VendorLead lead = lead(vendor, VendorLeadStatus.NEW);
        UpdateVendorLeadStatusRequest request = new UpdateVendorLeadStatusRequest();
        request.setStatus(VendorLeadStatus.DECLINED);
        request.setReason(" ");

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(901L, 501L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> vendorLeadService.updateStatus(901L, request, vendorAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(exception.getReason()).isEqualTo("Decline reason is required");
                        });
        verify(vendorLeadRepository, never()).save(any());
        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any());
    }

    @Test
    void decliningLeadStoresAndReturnsCustomerVisibleReason() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);
        VendorLead lead = lead(vendor, VendorLeadStatus.NEW);
        UpdateVendorLeadStatusRequest request = new UpdateVendorLeadStatusRequest();
        request.setStatus(VendorLeadStatus.DECLINED);
        request.setReason("  Already booked for the event date.  ");

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(901L, 501L)).thenReturn(Optional.of(lead));
        when(vendorLeadRepository.save(any(VendorLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorLeadResponse response = vendorLeadService.updateStatus(901L, request, vendorAuth());

        assertThat(response.getStatus()).isEqualTo(VendorLeadStatus.DECLINED);
        assertThat(response.getDeclineReason()).isEqualTo("Already booked for the event date.");
        verify(notificationService).notifyUser(
                eq(lead.getCustomer()),
                eq(NotificationType.ENQUIRY),
                eq("Lead declined"),
                eq("Saffron Leaf Catering declined your enquiry. Reason: Already booked for the event date."),
                eq("/customer?tab=enquiries"));
    }

    @Test
    void getMyCustomerLeadsReturnsOnlyAuthenticatedCustomerLeads() {
        User customer = customer();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorLead lead = lead(vendor, VendorLeadStatus.BOOKED);

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(vendorLeadRepository.findByCustomer_IdOrderByCreatedAtDesc(101L)).thenReturn(List.of(lead));

        List<VendorLeadResponse> response = vendorLeadService.getMyCustomerLeads(customerAuth());

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(901L);
        assertThat(response.get(0).getVendorName()).isEqualTo("Saffron Leaf Catering");
        assertThat(response.get(0).getCustomerId()).isEqualTo("101");
        assertThat(response.get(0).getStatus()).isEqualTo(VendorLeadStatus.BOOKED);
    }

    @Test
    void vendorLeadResponseIdentifiesMarketplaceSourceAndContactPrivacy() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);
        VendorLead lead = lead(vendor, VendorLeadStatus.NEW);
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(801L);
        requirement.setPreferredContactChannel(PreferredContactChannel.IN_APP);
        requirement.setShareContactDetails(false);
        lead.setRequirement(requirement);
        lead.setCustomerName("VenueMart customer");
        lead.setCustomerPhone(null);
        lead.setCustomerEmail(null);

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByVendor_IdOrderByCreatedAtDesc(501L)).thenReturn(List.of(lead));

        VendorLeadResponse response = vendorLeadService.getMyLeads(vendorAuth()).getFirst();

        assertThat(response.getRequirementId()).isEqualTo(801L);
        assertThat(response.getSource()).isEqualTo("MARKETPLACE_REQUIREMENT");
        assertThat(response.isContactDetailsShared()).isFalse();
        assertThat(response.getPreferredContactChannel()).isEqualTo(PreferredContactChannel.IN_APP);
        assertThat(response.getCustomerName()).isEqualTo("VenueMart customer");
        assertThat(response.getCustomerPhone()).isNull();
        assertThat(response.getCustomerEmail()).isNull();
    }

    @Test
    void acceptedMarketplaceLeadReleasesContactOnlyForSelectedVendor() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);
        VendorLead lead = lead(vendor, VendorLeadStatus.BOOKED);
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(801L);
        requirement.setPreferredContactChannel(PreferredContactChannel.IN_APP);
        requirement.setShareContactDetails(false);
        lead.setRequirement(requirement);
        lead.setCustomerName("VenueMart customer");
        lead.setCustomerPhone(null);
        lead.setCustomerEmail(null);
        lead.setContactDetailsReleased(true);

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByVendor_IdOrderByCreatedAtDesc(501L)).thenReturn(List.of(lead));

        VendorLeadResponse response = vendorLeadService.getMyLeads(vendorAuth()).getFirst();

        assertThat(response.isContactDetailsShared()).isTrue();
        assertThat(response.getCustomerName()).isEqualTo("Priya Raman");
        assertThat(response.getCustomerPhone()).isEqualTo("9000000001");
        assertThat(response.getCustomerEmail()).isEqualTo("priya@example.com");
    }

    @Test
    void vendorCannotManuallyBookMarketplaceLead() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);
        VendorLead lead = lead(vendor, VendorLeadStatus.QUOTE_SENT);
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(801L);
        lead.setRequirement(requirement);
        UpdateVendorLeadStatusRequest request = new UpdateVendorLeadStatusRequest();
        request.setStatus(VendorLeadStatus.BOOKED);

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(901L, 501L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> vendorLeadService.updateStatus(901L, request, vendorAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("Marketplace bookings are confirmed when the customer accepts a quote");
                });
        verify(vendorLeadRepository, never()).save(any());
    }

    @Test
    void vendorCanOpenAssignedLeadByOpaqueReference() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);
        VendorLead lead = lead(vendor, VendorLeadStatus.NEW);

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByPublicReferenceAndVendor_Id(
                "LEAD-0123456789ABCDEF0123",
                501L))
                .thenReturn(Optional.of(lead));

        VendorLeadResponse response = vendorLeadService.getLeadByReference(
                "LEAD-0123456789ABCDEF0123",
                vendorAuth());

        assertThat(response.getId()).isEqualTo(901L);
        assertThat(response.getLeadReference()).isEqualTo("LEAD-0123456789ABCDEF0123");
        assertThat(response.getVendorId()).isEqualTo("501");
    }

    @Test
    void vendorCannotOpenLeadThatIsNotAssignedToItsAccount() {
        User vendorUser = vendorUser();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        vendor.setUser(vendorUser);

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByPublicReferenceAndVendor_Id(
                "LEAD-ABCDEF0123456789ABCD",
                501L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorLeadService.getLeadByReference(
                "LEAD-ABCDEF0123456789ABCD",
                vendorAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Lead not found");
                });
    }

    private CreateVendorLeadRequest createRequest(String vendorId) {
        CreateVendorLeadRequest request = new CreateVendorLeadRequest();
        request.setVendorId(vendorId);
        request.setService("Wedding catering");
        request.setEventType("Wedding");
        request.setEventDate(LocalDate.parse("2026-08-12"));
        request.setLocation("Adyar, Chennai");
        request.setBudget(new BigDecimal("450000"));
        request.setNotes("Dinner for 500 guests.");
        return request;
    }

    private VendorLead lead(Vendors vendor, VendorLeadStatus status) {
        VendorLead lead = new VendorLead();
        lead.setId(901L);
        lead.setPublicReference("LEAD-0123456789ABCDEF0123");
        lead.setVendor(vendor);
        lead.setCustomer(customer());
        lead.setCustomerName("Priya Raman");
        lead.setCustomerPhone("9000000001");
        lead.setCustomerEmail("priya@example.com");
        lead.setService("Wedding catering");
        lead.setEventType("Wedding");
        lead.setEventDate(LocalDate.parse("2026-08-12"));
        lead.setLocation("Adyar, Chennai");
        lead.setBudget(new BigDecimal("450000"));
        lead.setStatus(status);
        return lead;
    }

    private Vendors vendor(VendorStatus status) {
        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setVendorName("Manoj Krishnan");
        vendor.setStatus(status);
        return vendor;
    }

    private User customer() {
        User user = new User();
        user.setId(101L);
        user.setFullName("Priya Raman");
        user.setPhone("9000000001");
        user.setEmail("priya@example.com");
        user.setStatus("ACTIVE");
        return user;
    }

    private User vendorUser() {
        User user = new User();
        user.setId(301L);
        user.setFullName("Manoj Krishnan");
        user.setStatus("ACTIVE");
        return user;
    }

    private Authentication customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "101",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private Authentication vendorAuth() {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
    }
}
