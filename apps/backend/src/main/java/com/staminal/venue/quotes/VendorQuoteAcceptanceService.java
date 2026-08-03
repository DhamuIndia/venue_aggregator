package com.staminal.venue.quotes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorQuoteStatus;
import com.staminal.venue.enums.VendorServiceBookingStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.quotes.dto.QuoteAcceptanceResponse;
import com.staminal.venue.quotes.dto.VendorQuoteResponse;
import com.staminal.venue.requirements.CustomerRequirement;
import com.staminal.venue.requirements.CustomerRequirementRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendorbookings.VendorServiceBooking;
import com.staminal.venue.vendorbookings.VendorServiceBookingRepository;
import com.staminal.venue.vendorbookings.VendorServiceBookingService;
import com.staminal.venue.vendors.Entity.Vendors;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class VendorQuoteAcceptanceService {

    private final VendorQuoteRepository vendorQuoteRepository;
    private final VendorLeadRepository vendorLeadRepository;
    private final CustomerRequirementRepository requirementRepository;
    private final VendorServiceBookingRepository bookingRepository;
    private final VendorServiceBookingService bookingService;
    private final VendorQuoteService quoteService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public QuoteAcceptanceResponse accept(Long quoteId, Authentication authentication) {
        User customer = currentCustomer(authentication);
        VendorQuote initialQuote = vendorQuoteRepository.findByIdAndLead_Customer_Id(quoteId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found"));

        CustomerRequirement requirement = lockRequirement(initialQuote, customer);
        VendorQuote quote = vendorQuoteRepository.findOwnedForUpdate(quoteId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found"));
        validateAcceptable(quote, requirement);

        if (bookingRepository.existsByQuote_Id(quoteId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This quote has already been accepted");
        }

        Instant acceptedAt = Instant.now();
        VendorLead selectedLead = quote.getLead();
        quote.setStatus(VendorQuoteStatus.ACCEPTED);
        quote.setShortlisted(false);
        quote.setShortlistedAt(null);
        selectedLead.setStatus(VendorLeadStatus.BOOKED);
        selectedLead.setDeclineReason(null);
        selectedLead.setContactDetailsReleased(true);
        if (requirement != null) {
            requirement.setStatus(CustomerRequirementStatus.CLOSED);
        }

        List<VendorQuote> requirementQuotes = requirement == null
                ? new ArrayList<>(List.of(quote))
                : new ArrayList<>(vendorQuoteRepository.findByLead_Requirement_IdOrderByUpdatedAtDesc(requirement.getId()));
        if (requirementQuotes.stream().noneMatch(candidate -> candidate.getId().equals(quote.getId()))) {
            requirementQuotes.add(quote);
        }

        List<VendorQuote> notSelectedQuotes = requirementQuotes.stream()
                .filter(candidate -> !candidate.getId().equals(quote.getId()))
                .filter(candidate -> candidate.getStatus() == VendorQuoteStatus.SENT)
                .toList();
        notSelectedQuotes.forEach(candidate -> {
            candidate.setStatus(VendorQuoteStatus.NOT_SELECTED);
            candidate.setShortlisted(false);
            candidate.setShortlistedAt(null);
        });

        List<VendorLead> notSelectedLeads = requirement == null
                ? List.of()
                : vendorLeadRepository.findByRequirement_IdOrderByCreatedAtDesc(requirement.getId())
                        .stream()
                        .filter(candidate -> !candidate.getId().equals(selectedLead.getId()))
                        .filter(candidate -> isOpenLeadStatus(candidate.getStatus()))
                        .toList();
        notSelectedLeads.forEach(candidate -> candidate.setStatus(VendorLeadStatus.NOT_SELECTED));

        vendorQuoteRepository.save(quote);
        if (!notSelectedQuotes.isEmpty()) {
            vendorQuoteRepository.saveAll(notSelectedQuotes);
        }
        vendorLeadRepository.save(selectedLead);
        if (!notSelectedLeads.isEmpty()) {
            vendorLeadRepository.saveAll(notSelectedLeads);
        }
        if (requirement != null) {
            requirementRepository.save(requirement);
        }

        VendorServiceBooking booking = bookingRepository.save(toBooking(quote, acceptedAt));
        notifyParticipants(quote, notSelectedLeads);
        recordAudit(customer, quote, booking, notSelectedQuotes);

        List<VendorQuoteResponse> quoteResponses = requirementQuotes.stream()
                .map(quoteService::toCustomerResponse)
                .toList();
        return new QuoteAcceptanceResponse(
                quoteService.toCustomerResponse(quote),
                quoteResponses,
                bookingService.toResponse(booking));
    }

    private CustomerRequirement lockRequirement(VendorQuote quote, User customer) {
        CustomerRequirement requirement = quote.getLead().getRequirement();
        if (requirement == null) {
            return null;
        }
        return requirementRepository.findOwnedForUpdate(requirement.getId(), customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found"));
    }

    private void validateAcceptable(VendorQuote quote, CustomerRequirement requirement) {
        if (quote.getStatus() != VendorQuoteStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an active quote can be accepted");
        }
        if (quote.getValidUntil().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This quote has expired");
        }
        if (quote.getLead().getEventDate() != null && quote.getLead().getEventDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The event date has passed");
        }
        if (quote.getLead().getStatus() != VendorLeadStatus.QUOTE_SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The vendor is no longer offering this quote");
        }
        if (requirement != null && requirement.getStatus() != CustomerRequirementStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A vendor has already been selected for this requirement");
        }
    }

    private VendorServiceBooking toBooking(VendorQuote quote, Instant acceptedAt) {
        VendorLead lead = quote.getLead();
        VendorServiceBooking booking = new VendorServiceBooking();
        booking.setQuote(quote);
        booking.setLead(lead);
        booking.setRequirement(lead.getRequirement());
        booking.setVendor(quote.getVendor());
        booking.setCustomer(lead.getCustomer());
        booking.setService(lead.getService());
        booking.setPackageName(quote.getPackageName());
        booking.setEventType(lead.getEventType());
        booking.setEventDate(lead.getEventDate());
        booking.setLocation(lead.getLocation());
        booking.setAmount(quote.getAmount().add(defaultZero(quote.getAdditionalCharges())));
        booking.setStatus(VendorServiceBookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.NOT_STARTED);
        booking.setConfirmedAt(acceptedAt);
        return booking;
    }

    private void notifyParticipants(VendorQuote quote, List<VendorLead> notSelectedLeads) {
        VendorLead selectedLead = quote.getLead();
        String vendorName = vendorName(quote.getVendor());
        notificationService.notifyUser(
                selectedLead.getCustomer(),
                NotificationType.BOOKING,
                "Vendor booking confirmed",
                "Your " + selectedLead.getService() + " booking with " + vendorName + " is confirmed.",
                "/customer?tab=bookings");
        notificationService.notifyUser(
                quote.getVendor().getUser(),
                NotificationType.BOOKING,
                "Your quote was accepted",
                "The customer accepted your " + quote.getPackageName()
                        + " quotation. Contact details are now available.",
                "/vendor?tab=leads");

        for (VendorLead lead : notSelectedLeads) {
            notificationService.notifyUser(
                    lead.getVendor().getUser(),
                    NotificationType.ENQUIRY,
                    "Requirement closed",
                    "The customer selected another vendor for " + lead.getService() + ".",
                    "/vendor?tab=leads");
        }
    }

    private void recordAudit(
            User customer,
            VendorQuote quote,
            VendorServiceBooking booking,
            List<VendorQuote> notSelectedQuotes) {
        Map<String, Object> acceptanceMetadata = new LinkedHashMap<>();
        acceptanceMetadata.put("leadId", quote.getLead().getId());
        acceptanceMetadata.put("vendorId", quote.getVendor().getId());
        if (quote.getLead().getRequirement() != null) {
            acceptanceMetadata.put("requirementId", quote.getLead().getRequirement().getId());
        }
        auditService.record(new AuditCommand(
                customer.getId(),
                UserRole.CUSTOMER.name(),
                AuditAction.QUOTE_ACCEPTED,
                "VENDOR_QUOTE",
                String.valueOf(quote.getId()),
                "Customer accepted vendor quote",
                Map.of("status", VendorQuoteStatus.SENT.name()),
                Map.of("status", VendorQuoteStatus.ACCEPTED.name()),
                acceptanceMetadata));

        for (VendorQuote notSelected : notSelectedQuotes) {
            auditService.record(new AuditCommand(
                    customer.getId(),
                    UserRole.CUSTOMER.name(),
                    AuditAction.QUOTE_NOT_SELECTED,
                    "VENDOR_QUOTE",
                    String.valueOf(notSelected.getId()),
                    "Quote closed after customer selected another vendor",
                    Map.of("status", VendorQuoteStatus.SENT.name()),
                    Map.of("status", VendorQuoteStatus.NOT_SELECTED.name()),
                    Map.of("acceptedQuoteId", quote.getId())));
        }

        Map<String, Object> bookingMetadata = new LinkedHashMap<>();
        bookingMetadata.put("quoteId", quote.getId());
        bookingMetadata.put("leadId", quote.getLead().getId());
        if (quote.getLead().getRequirement() != null) {
            bookingMetadata.put("requirementId", quote.getLead().getRequirement().getId());
        }
        auditService.record(new AuditCommand(
                customer.getId(),
                UserRole.CUSTOMER.name(),
                AuditAction.VENDOR_SERVICE_BOOKING_CREATED,
                "VENDOR_SERVICE_BOOKING",
                String.valueOf(booking.getId()),
                "Vendor service booking created from accepted quote",
                null,
                Map.of(
                        "status", booking.getStatus().name(),
                        "amount", booking.getAmount()),
                bookingMetadata));
    }

    private User currentCustomer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean isCustomer = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_CUSTOMER"::equals);
        if (!isCustomer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CUSTOMER role is required");
        }

        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session");
        }
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is not active");
        }
        return customer;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isOpenLeadStatus(VendorLeadStatus status) {
        return status == VendorLeadStatus.NEW
                || status == VendorLeadStatus.INTERESTED
                || status == VendorLeadStatus.CONTACTED
                || status == VendorLeadStatus.QUOTE_SENT;
    }

    private String vendorName(Vendors vendor) {
        if (vendor.getBusinessName() != null && !vendor.getBusinessName().isBlank()) {
            return vendor.getBusinessName().trim();
        }
        return vendor.getVendorName() == null ? "Vendor" : vendor.getVendorName().trim();
    }
}
