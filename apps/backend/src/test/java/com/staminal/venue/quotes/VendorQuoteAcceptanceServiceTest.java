package com.staminal.venue.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorQuoteStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.requirements.CustomerRequirementRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendorbookings.VendorServiceBooking;
import com.staminal.venue.vendorbookings.VendorServiceBookingRepository;
import com.staminal.venue.vendorbookings.VendorServiceBookingService;
import com.staminal.venue.vendors.Entity.Vendors;

@ExtendWith(MockitoExtension.class)
class VendorQuoteAcceptanceServiceTest {

    @Mock
    private VendorQuoteRepository quoteRepository;
    @Mock
    private VendorLeadRepository leadRepository;
    @Mock
    private CustomerRequirementRepository requirementRepository;
    @Mock
    private VendorServiceBookingRepository bookingRepository;
    @Mock
    private VendorServiceBookingService bookingService;
    @Mock
    private VendorQuoteService quoteService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;

    private VendorQuoteAcceptanceService service;

    @BeforeEach
    void setUp() {
        service = new VendorQuoteAcceptanceService(
                quoteRepository,
                leadRepository,
                requirementRepository,
                bookingRepository,
                bookingService,
                quoteService,
                userRepository,
                notificationService,
                auditService);
    }

    @Test
    void acceptanceClosesRequirementCreatesBookingAndReleasesOnlySelectedContact() {
        User customer = customer();
        CustomerRequirement requirement = requirement(customer, CustomerRequirementStatus.OPEN);
        VendorQuote selected = quote(901L, lead(701L, vendor(501L, 301L), customer, requirement), "105000");
        VendorQuote competing = quote(902L, lead(702L, vendor(502L, 302L), customer, requirement), "95000");

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(quoteRepository.findByIdAndLead_Customer_Id(901L, 101L)).thenReturn(Optional.of(selected));
        when(requirementRepository.findOwnedForUpdate(801L, 101L)).thenReturn(Optional.of(requirement));
        when(quoteRepository.findOwnedForUpdate(901L, 101L)).thenReturn(Optional.of(selected));
        when(quoteRepository.findByLead_Requirement_IdOrderByUpdatedAtDesc(801L))
                .thenReturn(List.of(selected, competing));
        when(leadRepository.findByRequirement_IdOrderByCreatedAtDesc(801L))
                .thenReturn(List.of(selected.getLead(), competing.getLead()));
        when(bookingRepository.existsByQuote_Id(901L)).thenReturn(false);
        when(bookingRepository.save(any(VendorServiceBooking.class))).thenAnswer(invocation -> {
            VendorServiceBooking booking = invocation.getArgument(0);
            booking.setId(1001L);
            booking.setCreatedAt(Instant.now());
            booking.setUpdatedAt(Instant.now());
            return booking;
        });

        service.accept(901L, customerAuth());

        assertThat(selected.getStatus()).isEqualTo(VendorQuoteStatus.ACCEPTED);
        assertThat(selected.getLead().getStatus()).isEqualTo(VendorLeadStatus.BOOKED);
        assertThat(selected.getLead().isContactDetailsReleased()).isTrue();
        assertThat(competing.getStatus()).isEqualTo(VendorQuoteStatus.NOT_SELECTED);
        assertThat(competing.getLead().getStatus()).isEqualTo(VendorLeadStatus.NOT_SELECTED);
        assertThat(competing.getLead().isContactDetailsReleased()).isFalse();
        assertThat(requirement.getStatus()).isEqualTo(CustomerRequirementStatus.CLOSED);

        ArgumentCaptor<VendorServiceBooking> bookingCaptor = ArgumentCaptor.forClass(VendorServiceBooking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        VendorServiceBooking booking = bookingCaptor.getValue();
        assertThat(booking.getQuote()).isSameAs(selected);
        assertThat(booking.getRequirement()).isSameAs(requirement);
        assertThat(booking.getAmount()).isEqualByComparingTo("110000");
        assertThat(booking.getCustomer()).isSameAs(customer);

        verify(notificationService, atLeast(3)).notifyUser(any(), any(), any(), any(), any());
        verify(auditService, atLeast(3)).record(any(AuditCommand.class));
    }

    @Test
    void closedRequirementRejectsSecondAcceptanceBeforeAnyBookingWrite() {
        User customer = customer();
        CustomerRequirement requirement = requirement(customer, CustomerRequirementStatus.CLOSED);
        VendorQuote selected = quote(901L, lead(701L, vendor(501L, 301L), customer, requirement), "105000");

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(quoteRepository.findByIdAndLead_Customer_Id(901L, 101L)).thenReturn(Optional.of(selected));
        when(requirementRepository.findOwnedForUpdate(801L, 101L)).thenReturn(Optional.of(requirement));
        when(quoteRepository.findOwnedForUpdate(901L, 101L)).thenReturn(Optional.of(selected));

        assertThatThrownBy(() -> service.accept(901L, customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("A vendor has already been selected for this requirement");
                });
        verify(bookingRepository, never()).save(any());
        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any());
    }

    @Test
    void customerCannotAcceptQuoteAfterVendorDeclinesLead() {
        User customer = customer();
        CustomerRequirement requirement = requirement(customer, CustomerRequirementStatus.OPEN);
        VendorQuote selected = quote(901L, lead(701L, vendor(501L, 301L), customer, requirement), "105000");
        selected.getLead().setStatus(VendorLeadStatus.DECLINED);

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(quoteRepository.findByIdAndLead_Customer_Id(901L, 101L)).thenReturn(Optional.of(selected));
        when(requirementRepository.findOwnedForUpdate(801L, 101L)).thenReturn(Optional.of(requirement));
        when(quoteRepository.findOwnedForUpdate(901L, 101L)).thenReturn(Optional.of(selected));

        assertThatThrownBy(() -> service.accept(901L, customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("The vendor is no longer offering this quote");
                });
        verify(bookingRepository, never()).save(any());
    }

    private VendorQuote quote(Long id, VendorLead lead, String amount) {
        VendorQuote quote = new VendorQuote();
        quote.setId(id);
        quote.setLead(lead);
        quote.setVendor(lead.getVendor());
        quote.setAmount(new BigDecimal(amount));
        quote.setAdditionalCharges(new BigDecimal("5000"));
        quote.setPackageName("Wedding stories");
        quote.setServiceDescription("Photography coverage");
        quote.setInclusions(List.of("Edited album"));
        quote.setValidUntil(LocalDate.now().plusDays(10));
        quote.setStatus(VendorQuoteStatus.SENT);
        return quote;
    }

    private VendorLead lead(
            Long id,
            Vendors vendor,
            User customer,
            CustomerRequirement requirement) {
        VendorLead lead = new VendorLead();
        lead.setId(id);
        lead.setVendor(vendor);
        lead.setCustomer(customer);
        lead.setRequirement(requirement);
        lead.setCustomerName("VenueMart customer");
        lead.setService("Photography");
        lead.setEventType("Wedding");
        lead.setEventDate(LocalDate.now().plusDays(30));
        lead.setLocation("Adyar, Chennai");
        lead.setStatus(VendorLeadStatus.QUOTE_SENT);
        return lead;
    }

    private CustomerRequirement requirement(User customer, CustomerRequirementStatus status) {
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(801L);
        requirement.setCustomer(customer);
        requirement.setStatus(status);
        return requirement;
    }

    private Vendors vendor(Long vendorId, Long userId) {
        User user = new User();
        user.setId(userId);
        user.setFullName("Vendor " + vendorId);
        user.setStatus("ACTIVE");
        Vendors vendor = new Vendors();
        vendor.setId(vendorId);
        vendor.setUser(user);
        vendor.setBusinessName("Studio " + vendorId);
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

    private Authentication customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "101",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
