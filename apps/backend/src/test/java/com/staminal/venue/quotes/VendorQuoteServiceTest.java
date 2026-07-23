package com.staminal.venue.quotes;

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

import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorQuoteStatus;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.quotes.dto.UpsertVendorQuoteRequest;
import com.staminal.venue.quotes.dto.UpdateQuoteShortlistRequest;
import com.staminal.venue.quotes.dto.VendorQuoteResponse;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class VendorQuoteServiceTest {

    @Mock
    private VendorQuoteRepository vendorQuoteRepository;

    @Mock
    private VendorLeadRepository vendorLeadRepository;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    private VendorQuoteService service;

    @BeforeEach
    void setUp() {
        service = new VendorQuoteService(
                vendorQuoteRepository,
                vendorLeadRepository,
                vendorRepository,
                userRepository,
                notificationService,
                auditService);
    }

    @Test
    void approvedVendorCreatesStructuredQuoteAndCustomerNotification() {
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorLead lead = lead(vendor, VendorLeadStatus.INTERESTED, true);
        when(userRepository.findById(301L)).thenReturn(Optional.of(vendor.getUser()));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(701L, 501L)).thenReturn(Optional.of(lead));
        when(vendorQuoteRepository.findByLead_Id(701L)).thenReturn(Optional.empty());
        when(vendorQuoteRepository.save(any(VendorQuote.class))).thenAnswer(invocation -> {
            VendorQuote quote = invocation.getArgument(0);
            quote.setId(901L);
            quote.onCreate();
            return quote;
        });
        when(vendorLeadRepository.save(any(VendorLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorQuoteResponse response = service.saveQuote(701L, request(), vendorAuth());

        ArgumentCaptor<VendorQuote> quoteCaptor = ArgumentCaptor.forClass(VendorQuote.class);
        verify(vendorQuoteRepository).save(quoteCaptor.capture());
        VendorQuote saved = quoteCaptor.getValue();
        assertThat(saved.getLead()).isSameAs(lead);
        assertThat(saved.getVendor()).isSameAs(vendor);
        assertThat(saved.getInclusions()).containsExactly("Candid photography", "Edited album");
        assertThat(saved.getStatus()).isEqualTo(VendorQuoteStatus.SENT);
        assertThat(lead.getStatus()).isEqualTo(VendorLeadStatus.QUOTE_SENT);
        assertThat(response.totalAmount()).isEqualByComparingTo("105000");
        assertThat(response.requirementId()).isEqualTo(801L);
        verify(notificationService).notifyUser(
                lead.getCustomer(),
                NotificationType.ENQUIRY,
                "New vendor quotation",
                "Framecraft Weddings sent a quote for Photography.",
                "/customer?tab=requirements");
        verify(auditService).record(any(AuditCommand.class));
    }

    @Test
    void existingQuoteIsUpdatedInsteadOfDuplicated() {
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorLead lead = lead(vendor, VendorLeadStatus.QUOTE_SENT, false);
        VendorQuote existing = quote(lead, vendor);
        existing.setShortlisted(true);
        existing.setShortlistedAt(java.time.Instant.now());
        when(userRepository.findById(301L)).thenReturn(Optional.of(vendor.getUser()));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(701L, 501L)).thenReturn(Optional.of(lead));
        when(vendorQuoteRepository.findByLead_Id(701L)).thenReturn(Optional.of(existing));
        when(vendorQuoteRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));
        when(vendorLeadRepository.save(lead)).thenReturn(lead);

        VendorQuoteResponse response = service.saveQuote(701L, request(), vendorAuth());

        assertThat(response.id()).isEqualTo(901L);
        assertThat(existing.getAmount()).isEqualByComparingTo("100000");
        assertThat(existing.isShortlisted()).isFalse();
        assertThat(existing.getShortlistedAt()).isNull();
        verify(vendorQuoteRepository).save(existing);
        verify(notificationService).notifyUser(
                eq(lead.getCustomer()),
                eq(NotificationType.ENQUIRY),
                eq("Vendor quotation updated"),
                any(String.class),
                eq("/customer?tab=enquiries"));
    }

    @Test
    void vendorCannotQuoteAnotherVendorsLead() {
        Vendors vendor = vendor(VendorStatus.APPROVED);
        when(userRepository.findById(301L)).thenReturn(Optional.of(vendor.getUser()));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(701L, 501L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveQuote(701L, request(), vendorAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(vendorQuoteRepository, never()).save(any());
    }

    @Test
    void quoteValidityCannotExtendBeyondEventDate() {
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorLead lead = lead(vendor, VendorLeadStatus.NEW, true);
        UpsertVendorQuoteRequest request = new UpsertVendorQuoteRequest(
                new BigDecimal("100000"),
                "Wedding stories",
                "Photography coverage.",
                List.of("Candid photography"),
                BigDecimal.ZERO,
                null,
                null,
                lead.getEventDate().plusDays(1));
        when(userRepository.findById(301L)).thenReturn(Optional.of(vendor.getUser()));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(701L, 501L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.saveQuote(701L, request, vendorAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo("Quote validity date cannot be after the event date"));
        verify(vendorQuoteRepository, never()).save(any());
    }

    @Test
    void terminalLeadCannotReceiveQuote() {
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorLead lead = lead(vendor, VendorLeadStatus.DECLINED, true);
        when(userRepository.findById(301L)).thenReturn(Optional.of(vendor.getUser()));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorLeadRepository.findByIdAndVendor_Id(701L, 501L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.saveQuote(701L, request(), vendorAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(vendorQuoteRepository, never()).save(any());
    }

    @Test
    void customerQuoteListIsScopedToAuthenticatedCustomer() {
        User customer = customer();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorLead lead = lead(vendor, VendorLeadStatus.QUOTE_SENT, true);
        VendorQuote quote = quote(lead, vendor);
        quote.setShortlisted(true);
        quote.setShortlistedAt(java.time.Instant.now());
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(vendorQuoteRepository.findByLead_Customer_IdOrderByUpdatedAtDesc(101L)).thenReturn(List.of(quote));

        List<VendorQuoteResponse> response = service.getMyCustomerQuotes(customerAuth());

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().vendorName()).isEqualTo("Framecraft Weddings");
        assertThat(response.getFirst().shortlisted()).isTrue();
        assertThat(response.getFirst().shortlistedAt()).isNotNull();
        verify(vendorQuoteRepository).findByLead_Customer_IdOrderByUpdatedAtDesc(101L);
    }

    @Test
    void customerCanShortlistOwnedActiveQuote() {
        User customer = customer();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorQuote quote = quote(lead(vendor, VendorLeadStatus.QUOTE_SENT, true), vendor);
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(vendorQuoteRepository.findByIdAndLead_Customer_Id(901L, 101L)).thenReturn(Optional.of(quote));
        when(vendorQuoteRepository.save(quote)).thenAnswer(invocation -> invocation.getArgument(0));

        VendorQuoteResponse response = service.updateShortlist(
                901L,
                new UpdateQuoteShortlistRequest(true),
                customerAuth());

        assertThat(response.shortlisted()).isTrue();
        assertThat(response.shortlistedAt()).isNotNull();
        assertThat(quote.isShortlisted()).isTrue();
        ArgumentCaptor<AuditCommand> auditCaptor = ArgumentCaptor.forClass(AuditCommand.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.QUOTE_SHORTLISTED);
        assertThat(auditCaptor.getValue().actorUserId()).isEqualTo(101L);
    }

    @Test
    void customerCannotShortlistExpiredQuote() {
        User customer = customer();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorQuote quote = quote(lead(vendor, VendorLeadStatus.QUOTE_SENT, true), vendor);
        quote.setValidUntil(LocalDate.now().minusDays(1));
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(vendorQuoteRepository.findByIdAndLead_Customer_Id(901L, 101L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.updateShortlist(
                901L,
                new UpdateQuoteShortlistRequest(true),
                customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(exception.getReason()).isEqualTo("Expired quotes cannot be shortlisted");
                        });
        verify(vendorQuoteRepository, never()).save(any());
    }

    @Test
    void customerCannotShortlistInactiveQuote() {
        User customer = customer();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorQuote quote = quote(lead(vendor, VendorLeadStatus.QUOTE_SENT, true), vendor);
        quote.setStatus(VendorQuoteStatus.WITHDRAWN);
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(vendorQuoteRepository.findByIdAndLead_Customer_Id(901L, 101L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.updateShortlist(
                901L,
                new UpdateQuoteShortlistRequest(true),
                customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(exception.getReason()).isEqualTo("Only active quotes can be shortlisted");
                        });
        verify(vendorQuoteRepository, never()).save(any());
    }

    @Test
    void customerCanRemoveExpiredQuoteFromShortlist() {
        User customer = customer();
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorQuote quote = quote(lead(vendor, VendorLeadStatus.QUOTE_SENT, true), vendor);
        quote.setValidUntil(LocalDate.now().minusDays(1));
        quote.setShortlisted(true);
        quote.setShortlistedAt(java.time.Instant.now());
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(vendorQuoteRepository.findByIdAndLead_Customer_Id(901L, 101L)).thenReturn(Optional.of(quote));
        when(vendorQuoteRepository.save(quote)).thenAnswer(invocation -> invocation.getArgument(0));

        VendorQuoteResponse response = service.updateShortlist(
                901L,
                new UpdateQuoteShortlistRequest(false),
                customerAuth());

        assertThat(response.shortlisted()).isFalse();
        assertThat(response.shortlistedAt()).isNull();
    }

    @Test
    void customerCannotReadOrShortlistAnotherCustomersQuote() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(vendorQuoteRepository.findByIdAndLead_Customer_Id(901L, 101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateShortlist(
                901L,
                new UpdateQuoteShortlistRequest(true),
                customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(vendorQuoteRepository, never()).save(any());
    }

    @Test
    void vendorQuoteListDoesNotExposeCustomerShortlistDecision() {
        Vendors vendor = vendor(VendorStatus.APPROVED);
        VendorQuote quote = quote(lead(vendor, VendorLeadStatus.QUOTE_SENT, true), vendor);
        quote.setShortlisted(true);
        quote.setShortlistedAt(java.time.Instant.now());
        when(userRepository.findById(301L)).thenReturn(Optional.of(vendor.getUser()));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorQuoteRepository.findByVendor_IdOrderByUpdatedAtDesc(501L)).thenReturn(List.of(quote));

        VendorQuoteResponse response = service.getMyVendorQuotes(vendorAuth()).getFirst();

        assertThat(response.shortlisted()).isFalse();
        assertThat(response.shortlistedAt()).isNull();
    }

    private UpsertVendorQuoteRequest request() {
        return new UpsertVendorQuoteRequest(
                new BigDecimal("100000"),
                "Wedding stories",
                "Eight hours of wedding photography.",
                List.of("Candid photography", "Edited album"),
                new BigDecimal("5000"),
                "Travel outside Chennai.",
                "Delivery in six weeks.",
                LocalDate.now().plusDays(10));
    }

    private VendorQuote quote(VendorLead lead, Vendors vendor) {
        VendorQuote quote = new VendorQuote();
        quote.setId(901L);
        quote.setLead(lead);
        quote.setVendor(vendor);
        quote.setAmount(new BigDecimal("90000"));
        quote.setPackageName("Classic");
        quote.setServiceDescription("Classic photography package.");
        quote.setInclusions(List.of("Photography"));
        quote.setAdditionalCharges(BigDecimal.ZERO);
        quote.setValidUntil(LocalDate.now().plusDays(5));
        quote.setStatus(VendorQuoteStatus.SENT);
        quote.onCreate();
        return quote;
    }

    private VendorLead lead(Vendors vendor, VendorLeadStatus status, boolean marketplace) {
        VendorLead lead = new VendorLead();
        lead.setId(701L);
        lead.setVendor(vendor);
        lead.setCustomer(customer());
        lead.setCustomerName("VenueMart customer");
        lead.setService("Photography");
        lead.setEventType("Wedding");
        lead.setEventDate(LocalDate.now().plusDays(30));
        lead.setLocation("Adyar, Chennai");
        lead.setStatus(status);
        if (marketplace) {
            CustomerRequirement requirement = new CustomerRequirement();
            requirement.setId(801L);
            lead.setRequirement(requirement);
        }
        return lead;
    }

    private Vendors vendor(VendorStatus status) {
        User user = vendorUser();
        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setUser(user);
        vendor.setBusinessName("Framecraft Weddings");
        vendor.setVendorName("Akhil");
        vendor.setStatus(status);
        return vendor;
    }

    private User vendorUser() {
        User user = new User();
        user.setId(301L);
        user.setFullName("Akhil");
        user.setStatus("ACTIVE");
        return user;
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

    private Authentication vendorAuth() {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
    }

    private Authentication customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "101",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
